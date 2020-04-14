(ns kcu.crud
  (:require
   [kcu.utils :as u]))


(defn- new-entity-id [_db]
  (u/random-uuid-string))


(defn new-db [options]
  {:entities {}
   :options options})


(defn entity [db id]
  (get-in db [:entities id]))


(defn mandatory-entity [db id]
  (if-let [entity (entity db id)]
    entity
    (throw (ex-info (str "Mandatory Entity `" id "` does not exist.")
                    {:missing-entity-id id}))))


(defn entities [db ids]
  (map #(entity db %) ids))


(defn mandatory-entities [db ids]
  (map #(mandatory-entity db %) ids))


(defn children-ids [db parent-id]
   (-> db (mandatory-entity parent-id) :db/children))


(defn children [db parent-id]
  (mandatory-entities
   db (children-ids db parent-id)))


(defn all-entities [db]
  (-> db :entities vals))


(defn find-entities [db filter-predicate]
  (->> db :entities (filter filter-predicate)))


(defn- maybe-assoc-id [db entity]
  (if (get entity :db/id)
    entity
    (assoc entity :db/id (new-entity-id db))))


(defn- update-entity [db entity]
  (let [entity (maybe-assoc-id db entity)
        entity (assoc entity :db/modified (u/current-time-millis))]
    (update-in db
               [:entities (get entity :db/id)]
               merge entity)))


(defn upd
  [db entity-or-entities]
  (if (map? entity-or-entities)
    (update-entity db entity-or-entities)
    (reduce update-entity db entity-or-entities)))



(defn add-child
  [db parent-id child]
  (let [parent (mandatory-entity db parent-id)
        child (maybe-assoc-id db child)
        child-id (get child :db/id)
        child (assoc child :db/parent parent-id)
        child (assoc child :db/modified (u/current-time-millis))
        parent (update parent :db/children #(conj (or % #{}) child-id))
        parent (assoc parent :db/modified (u/current-time-millis))]
    (-> db
        (assoc-in [:entities parent-id] parent)
        (assoc-in [:entities child-id] child))))
