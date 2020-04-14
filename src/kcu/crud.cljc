(ns kcu.crud
  (:require
   [kcu.utils :as u]))


(defn- new-entity-id [_db]
  (u/random-uuid))


(defn new-db [options]
  {:entities {}
   :options options})


(defn rd
  ([db]
   (-> db :entities vals))
  ([db filter-predicate]
   (->> db :entities (filter filter-predicate))))


(defn- maybe-assoc-id [db entity]
  (if (get entity :db/id)
    entity
    (assoc entity :db/id (new-entity-id db))))


(defn- update-entity [db entity]
  (let [entity (maybe-assoc-id db entity)]
    (update-in db
               [:entities (get entity :db/id)]
               merge entity)))


(defn upd
  [db entity-or-entities]
  (if (map? entity-or-entities)
    (update-entity db entity-or-entities)
    (reduce update-entity db entity-or-entities)))
