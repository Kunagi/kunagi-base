(ns conform.api
  (:require
   [clojure.spec.alpha :as s]))


(defn- validate-val
  [value spec function-identifier-key]
  (if (s/valid? spec value)
    value
    (throw (ex-info (str function-identifier-key " failed."
                         "\n"
                         "Spec [ " spec " ] failed for value [ " (print-str value) " ].")
                    {:spec-explain (s/explain-str spec value)}))))


(defn- validate-map
  [m spec function-identifier-key]
  (if (s/valid? spec m)
    m
    (throw (ex-info (str function-identifier-key " failed."
                         "\n"
                         "Spec [ " spec " ] failed for map.")
                    {:spec-explain (s/explain-str spec m)}))))


(defn validate
  [function-identifier-key & validations]
  (doseq [[type val spec] validations]
    (case type
      :map (validate-map val spec function-identifier-key)
      (validate-val val spec function-identifier-key))))


