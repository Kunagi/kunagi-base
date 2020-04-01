(ns kcu.registry
  #?(:cljs (:require-macros [kcu.registry]))
  (:require
   [clojure.spec.alpha :as s]
   [kcu.utils :as u]))


(s/def ::entity-type keyword?)
(s/def ::entity-key (s/or :keyword keyword?
                          :vector (s/coll-of (s/or :k keyword?
                                                   :s string?)
                                             :kind vector?
                                             :min-count 1)))
(s/def ::entity map?)

(defonce REGISTRY (atom {}))

(defn register
  [entity-type k entity]
  (u/assert-spec ::entity-type entity-type)
  (u/assert-spec ::entity-key k)
  (u/assert-spec ::entity entity)
  (tap> [:inf ::register [entity-type k]])
  (swap! REGISTRY assoc-in [entity-type k] entity))


(defn maybe-entity
  [entity-type k]
  (get-in @REGISTRY [entity-type k]))


(defn entity
  [entity-type k]
  (or
   (get-in @REGISTRY [entity-type k])
   (throw (ex-info (str "Unknown `" entity-type "` identfied by `"
                        k "`. Known are `"
                        (into [] (keys (get @REGISTRY entity-type))) "`.")
                   {:requested-entity-type entity-type
                    :unknown-key k
                    :known-keys (into [] (keys (get @REGISTRY entity-type)))}))))


(defn entities
  [entity-type]
  (-> @REGISTRY (get entity-type) vals))


(defn entities-by [entity-type filter-k filter-v]
  (->> (entities entity-type)
       (filter #(= filter-v (get % filter-k)))))


(defn update-entity
  [entity-type k f & args]
  (swap! REGISTRY (fn [registry]
                    (update-in registry [entity-type k]
                      (fn [entity]
                        (apply f entity args))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; bounded context


;; TODO (reg-bounded-context bc-name namespace)
(defn bounded-context [k]
  (cond
    (string? k) (keyword
                 (if (-> k (#?(:clj .contains :cljs .includes) "."))
                   (-> k (.substring 0 (-> k (.indexOf "."))))
                   k))
    (simple-keyword? k) (bounded-context (name k))
    (qualified-keyword? k) (bounded-context (namespace k))
    :else (throw (ex-info (str "Can not extract bounded context from `"
                               k "´.")
                          {:k k}))))

(bounded-context :hello.world)

;; (defmacro def-event
;;   [sym & args]
;;   (let [[options] args
;;         event-name (keyword (str (ns-name *ns*)) (name sym))]
;;     `(def ~sym (reg-event ~event-name ~options))))

#_(macroexpand-1 '(def-event ticket-gezogen {}))
