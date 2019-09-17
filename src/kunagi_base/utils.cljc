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
