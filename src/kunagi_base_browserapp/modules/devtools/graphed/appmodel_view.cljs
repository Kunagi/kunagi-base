(ns kunagi-base-browserapp.modules.devtools.graphed.appmodel-view
  (:require
   [mui-commons.graphed.api :as graphed]
   [kunagi-base.appmodel :as am]))

(defn q-modules []
  '[:find ?m
    :where
    [?m :module/id _]])


(defn n-appmodel [db]
  (let [modules (am/q db (q-modules))]
    {:graphed.node/childs-ids
      (map (fn [[module-id]] [:module module-id])
           modules)}))


(defn q-entity-models []
  '[:find ?ident
    :where
    [_ :entity-model/ident ?ident]])


(defn n-module [db [_ module-id]]
  (let [module (am/entity db module-id)
        entity-models (am/q db (q-entity-models))
        entity-models (remove
                       (fn [[type]]
                         (let [back-ref (keyword (name type) "_module")
                               entities (-> module back-ref)]
                           (empty? entities)))
                       entity-models)]
    {:graphed.node/name (-> module :module/ident)
     :graphed.node/side-text "M"
     :graphed.node/childs-ids
     (map
      (fn [[type]]
        [:entity-model-group module-id type])
      entity-models)}))


(defn n-entity-model-group [db [_ module-id type]]
  (let [module (am/entity db module-id)
        back-ref (keyword (name type) "_module")
        entities (-> module back-ref)]
    {:graphed.node/name (str type "s")
     :graphed.node/childs-ids
     (map (fn [entity] [:entity module-id type (-> entity :db/id)])
          entities)}))

(defn map-node [m ks]
  {:graphed.node/side-text "{"
   :graphed.node/childs
   (map (fn [k]
          {:graphed.node/horizontal? true
           :graphed.node/childs
           [{:graphed.node/name k}
            {:graphed.node/name (get m k)}]})
        ks)})


(defn n-entity [db [_ module-id type entity-id]]
  (let [entity (am/entity db entity-id)
        type-name (name type)
        entity-name (or (-> entity (get (keyword type-name "ident")))
                        (-> entity (get (keyword type-name "id"))))
        ks (keys entity)]
    {:graphed.node/name entity-name
     :graphed.node/childs
     [(map-node entity ks)]}))



(defrecord AppmodelView []
  graphed/GraphedView
  (root-node-id [this]
    [:appmodel])
  (node [this [type :as node-id]]
    (let [db (am/model-db)]
      (case type
        :appmodel (n-appmodel db)
        :module (n-module db node-id)
        :entity-model-group (n-entity-model-group db node-id)
        :entity (n-entity db node-id)
        {:graphed.node/name (str "Unsupported:" node-id)}))))
