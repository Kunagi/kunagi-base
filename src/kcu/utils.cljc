(ns kcu.utils
  (:refer-clojure :exclude [read-string])
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [#?(:cljs cljs.reader :clj clojure.edn) :refer [read-string]]))


(defn random-uuid-string []
  (str #?(:cljs (random-uuid)
          :clj  (java.util.UUID/randomUUID))))


(defn current-time-millis []
  #?(:cljs (.getTime (js/Date.))
     :clj  (System/currentTimeMillis)))


(defn as-optional-fn
  "Coerce `function-or-value` to function if not nil."
  [function-or-value]
  (when function-or-value
    (if (fn? function-or-value)
      function-or-value
      (constantly function-or-value))))


(defn getm
  "get mandatory"
  ([m k]
   (when-not m
     (throw (ex-info (str "No map to get key `" k "` from.")
                     {:k k})))
   (if-let [v (get m k)]
     v
     (throw (ex-info (str "No value for key `" k
                          "`. Keys: `" (keys m) "`.")
                     {:m m
                      :k k}))))
  ([m k spec]
   (let [v (getm m k)]
     (if (s/valid? spec v)
       v
       (throw (ex-info (str "Invalid value for key `" k "`. "
                            (s/explain-str spec v))
                       {:m m
                        :k k
                        :v v
                        :spec spec}))))))


(defn assert-spec
  ([spec value]
   (assert-spec spec value nil))
  ([spec value otherwise-msg]
   (if (s/valid? spec value)
     value
     (throw (ex-info (str (when otherwise-msg
                            (if (qualified-keyword? otherwise-msg)
                              (str "Assertion in "
                                   (pr-str otherwise-msg)
                                   " failed.\n")
                              (str otherwise-msg "\n")))
                          "Value does not conform to spec "
                          spec
                          ": "
                          (pr-str value))
                     {:spec spec
                      :value value
                      :spec-explain (s/explain-str spec value)})))))


(defn decode-edn
  [s]
  (when s
    (read-string s)))


(defn encode-edn
  ([value]
   (pr-str value))
  ([value pretty?]
   (if pretty?
     (with-out-str (pprint value))
     (pr-str value))))


(defn invoke-later!
  [offset-millis f]
  #?(:cljs (js/setTimeout f offset-millis)
     :clj (future
            (Thread/sleep offset-millis)
            (f))))
