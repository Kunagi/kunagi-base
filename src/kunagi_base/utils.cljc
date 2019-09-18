(ns kunagi-base.utils
  (:refer-clojure :exclude [assert])
  (:require
   [clojure.spec.alpha :as s]))


(defn new-uuid []
  (str #?(:cljs (random-uuid)
          :clj  (java.util.UUID/randomUUID))))


(defn current-time-millis []
  #?(:cljs (.getTime (js/Date.))
     :clj  (System/currentTimeMillis)))

(defmacro assert
  "Check if the given form is truthy, otherwise throw an exception with
  the given message and some additional context. Alternative to
  `assert` with Exceptions instead of Errors."
  [form otherwise-msg & values]
  `(or ~form
       (throw (ex-info ~otherwise-msg
                       (hash-map :form '~form
                                 ~@(mapcat (fn [v] [`'~v v]) values))))))

(defn assert-spec
  [spec value otherwise-msg]
  (if (s/valid? spec value)
    value
    (throw (ex-info (str otherwise-msg
                         "\nValue does not conform to spec "
                         spec
                         ": "
                         (pr-str value))
                    {:spec spec
                     :value value
                     :spec-explain (s/explain-str spec value)}))))


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
