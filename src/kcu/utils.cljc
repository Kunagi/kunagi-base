(ns kcu.utils
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


(defn getm
  "get mandatory"
  ([m k]
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
