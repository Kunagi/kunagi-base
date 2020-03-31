(ns kcu.registry
  #?(:cljs (:require-macros [kcu.registry]))
  (:require
   [clojure.spec.alpha :as s]
   [kcu.utils :as u]))


(s/def ::entity-type keyword?)
(s/def ::entity-key (s/or :keyword keyword?
                          :vector (s/coll-of keyword? :kind vector? :min-count 1)))
(s/def ::entity map?)

(defonce REGISTRY (atom {}))

(defn register
  [entity-type k entity]
  (u/assert-spec ::entity-type entity-type)
  (u/assert-spec ::entity-key k)
  (u/assert-spec ::entity entity)
  (tap> [:inf ::register [entity-type k]])
  (swap! REGISTRY assoc-in [entity-type k] entity))


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



;; (defmacro def-event
;;   [sym & args]
;;   (let [[options] args
;;         event-name (keyword (str (ns-name *ns*)) (name sym))]
;;     `(def ~sym (reg-event ~event-name ~options))))

#_(macroexpand-1 '(def-event ticket-gezogen {}))
