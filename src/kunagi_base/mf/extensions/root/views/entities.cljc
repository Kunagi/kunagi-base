(ns kunagi-base.mf.extensions.root.views.entities
  (:require
   [kunagi-base.mf.editor :as editor]
   [kunagi-base.mf.model :as model]
   [kunagi-base.mf.view :as view]))


(defn entity-kv-node [entity k]
  {:entity-id nil
   :text (str k)
   :childs [{:entity-id nil
             :text (str (get entity k))}]})


(defn entity-node [model entity-id]
  (let [entity (model/entity model entity-id)]
    {:entity-id entity-id
     :text entity-id
     :childs (map (fn [k] (entity-kv-node entity k)) (keys entity))}))


(defn root-node [editor]
  (let [model (-> editor :model)
        ids (model/ids model)]
    {:entity-id nil
     :text "all entities"
     :childs (map (partial entity-node model) ids)}))


(defrecord View [ident]
  view/View
  (view/node-tree [this editor entity-id]
    (root-node editor)))


(defn new-view []
  (->View :entities))
