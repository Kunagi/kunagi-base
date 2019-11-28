(ns kunagi.datumoj.schema
  (:require
   [clojure.spec.alpha :as s]
   [kunagi.datumoj.schema.entity :as entity]
   [kunagi.datumoj.schema.attr :as attr]))


(s/def ::ident simple-keyword?)
(s/def ::schema (s/keys :req-un [::ident]))


(defn complete [schema]
  (when-not (s/valid? ::schema schema)
    (throw (ex-info "Invalid schema"
                    {:spec   (s/explain-str ::schema schema)
                     :schema schema})))

  (cond-> schema

    (nil? (-> schema :ident))
    (assoc :ident :unnamed)

    true
    (update :entities (partial map #(entity/complete % schema)))))


(defn new-schema [spec]
  (complete spec))
