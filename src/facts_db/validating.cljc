(ns facts-db.validating
  (:require
   [clojure.spec.alpha :as s]))


(s/def :db/fact-name keyword?)
(s/def :db/fact-value any?)
(s/def :db/id any?)

(s/def :db/entity (s/and
                   (s/keys :req [:db/id])
                   (s/map-of :db/fact-name :db/fact-value)))

(s/def :db/entities (s/coll-of :db/entity))

(s/def :db/db (s/map-of :db/id :db/entity))


(defn validate-db
  "Validate and return the given `db`"
  [db]
  (if (s/valid? :db/db db)
    db
    (throw (ex-info "Invalid db"
                    {:db db
                     :spec-explain (s/explain-str :db/db db)}))))


(defn validate-entity
  [entity]
  "Validate and return the given `entity`"
  (if (s/valid? :db/entity entity)
    entity
    (throw (ex-info "Invalid entity"
                    {:entity entity}))))
