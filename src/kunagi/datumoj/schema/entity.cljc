(ns kunagi.datumoj.schema.entity
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]))


(s/def ::ident simple-keyword?)


(defn label-1
  [entity]
  (or (-> entity :texts :label-1)
      (-> entity :ident name string/capitalize)))

(defn label-n
  [entity]
  (or (-> entity :texts :label-n)
      (when-let [label (-> entity :texts :label-1)]
        (-> label string/capitalize (str "s")))
      (-> entity :ident name string/capitalize (str "s"))))
