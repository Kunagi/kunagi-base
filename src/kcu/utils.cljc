(ns kcu.utils
  (:require
   [clojure.spec.alpha :as s]))


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


;; (getm {:a :b} :c)
