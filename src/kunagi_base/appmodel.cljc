(ns kunagi-base.appmodel
  (:require
   [facts-db.api :as db]))


(def index-id "appmodel.index")


(defn- new-db []
  (-> (db/new-db)
      (db/update-facts
       [{:db/id index-id
         :index/modules []
         :index/current-module nil}])))




(defn entities [db index-ref pull-template]
  (-> db
      (db/tree index-id {index-ref pull-template})
      (get index-ref)))


(defn entity-by-ident [db type ident])


(defn entity-id [module-ident entity-type entity-ident]
  (str entity-type ":"
       (when module-ident (str module-ident ":"))
       entity-ident))


(defn- assoc-id [entity module-ident entity-type entity-ident]
  (if (:db/id entity)
    entity
    (assoc entity :db/id (entity-id module-ident entity-type entity-ident))))


(defn current-module-id [db]
  (db/fact db index-id :index/current-module))


(defn module-by-ident [db module-ident pull-template]
  (let [module-id (db/find-id db #(= module-ident (:module/ident %)))]
    (db/tree db module-id pull-template)))


;;;

(defonce !db (atom (new-db)))


(defn model []
  @!db)

(defn update-facts [facts]
  (swap! !db db/update-facts facts))


(defn register-entity
  [type entity]
  (let [db @!db
        module-id (current-module-id db)
        module-ident (db/fact db module-id :module/ident)
        entity-ident (get entity (keyword (name type) "ident"))]
    (update-facts
     [(-> entity
          (assoc-id module-ident type entity-ident)
          (assoc :appmodel/type type)
          (assoc (keyword (name type) "module") module-id)
          (assoc :db/add-ref-n [index-id (keyword "index" (str (name type) "s"))])
          (assoc :db/add-ref-1 [index-id :index/current-module]))])))


(defn def-module
  [module]
  (register-entity
   :module
   module))
