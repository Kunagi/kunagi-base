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


(defonce !db (atom (new-db)))


(defn model []
  @!db)


(defn entities [index-ref pull-template]
  (-> @!db
      (db/tree index-id {index-ref pull-template})
      (get index-ref)))


(defn- assoc-id [entity]
  (if (:db/id entity)
    entity
    (assoc entity :db/id (db/new-uuid))))


(defn current-module-id [db]
  (db/fact db index-id :index/current-module))


(defn update-facts [facts]
  (swap! !db db/update-facts facts))


(defn register-entity
  [type entity]
  (let [db @!db]
    (update-facts
     [(-> entity
          assoc-id
          (assoc :appmodel/type type)
          (assoc (keyword (name type) "module") (current-module-id db))
          (assoc :db/add-ref-n [index-id (keyword "index" (str (name type) "s"))])
          (assoc :db/add-ref-1 [index-id :index/current-module]))])))


(defn def-module
  [module]
  (register-entity
   :module
   module))
