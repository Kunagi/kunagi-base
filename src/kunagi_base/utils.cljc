(ns kunagi-base.utils
  (:refer-clojure :exclude [assert])
  #?(:cljs (:require-macros [kunagi-base.utils]))
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [kcu.utils :as kcu]))


(def new-uuid kcu/random-uuid-string)

(def current-time-millis kcu/current-time-millis)

;;; maps


(def deep-merge kcu/deep-merge)

;;; assertions


(defmacro assert
  "Check if the given form is truthy, otherwise throw an exception with
  the given message and some additional context. Alternative to
  `assert` with Exceptions instead of Errors."
  [form otherwise-msg & values]
  `(or ~form
       (throw (ex-info ~otherwise-msg
                       (hash-map :form '~form
                                 ~@(mapcat (fn [v] [`'~v v]) values))))))

;; (defn assert [form otherwise-msg & values])
;;   ;; FIXME

(def assert-spec kcu/assert-spec)


;;; search


(defn searchtext->words [s]
  (if (str/blank? s)
    nil
    (-> s
        .trim
        .toLowerCase
        (str/split #" "))))


(defn- text-contains-word? [text word]
  (when text
    (str/includes? text word)))


(defn- texts-contain-word? [texts word]
  (reduce
   (fn [ret text]
     (or ret (text-contains-word? text word)))
   false
   texts))


(defn texts-contain-words? [texts words]
  (let [texts (map
               (fn [s]
                 (when s (.toLowerCase s)))
               texts)]
    (reduce
     (fn [ret word]
       (let [ret (and ret (texts-contain-word? texts word))]
         ret))
     true
     words)))


;;; spec

(defn- assert-entity-attribute-conforms-to-spec
  [msg entity [k spec]]
  (let [v (get entity k)]
    (if (s/valid? spec v)
      entity
      (throw (ex-info (str (when msg (str msg " "))
                           "Entity attribute "
                           (pr-str k) " = " (pr-str v)
                           " does not conform to spec " (pr-str  spec) ".")
                      {:entity entity
                       :k k
                       :spec spec
                       :explain-str (s/explain-str spec v)})))))


(defn assert-entity
  ([entity spec]
   (assert-entity entity spec nil))
  ([entity spec error-message-prefix]
   (let [req-keys (-> spec :req)
         opt-keys (-> spec :opt)]
     (reduce
      (partial
       assert-entity-attribute-conforms-to-spec
       error-message-prefix)
      entity
      req-keys)
     (reduce
      (fn [entity [k spec :as attr]]
        (if (contains? entity k)
          (assert-entity-attribute-conforms-to-spec
           error-message-prefix entity attr)
          entity))
      entity
      opt-keys))
   entity))





;;;


(defn new-value-on-demand-map [init-value-f]
  (let [!a (atom {})
        get-f (fn [path]
                (locking !a
                  (if-let [v (get-in @!a path)]
                    v
                    (let [v (init-value-f path)]
                      (swap! !a assoc-in path v)
                      v))))]
    {:map-atom !a
     :get-f get-f}))


;;; worker agents


#?(:clj
   (defn new-worker-agent
     [initial-value
      auto-init-f
      handle-error-f]
     (let [!ag (agent initial-value
                      :error-handler handle-error-f)]
       (when auto-init-f (send-off !ag auto-init-f))
       !ag)))


#?(:clj
   (defn new-worker-agents-pool [handle-error-f init-f]
     (new-value-on-demand-map
      (fn [path]
        (new-worker-agent
         nil
         (fn [_]
           (init-f path))
         handle-error-f)))))
