(ns kcu.ui
  (:require-macros [kcu.ui])
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.registry :as registry]))


(defn reg-component
  [id component-f model-type options]
  (registry/register
   :ui-component
   id
   (merge
    {:id id
     :f component-f
     :model-type model-type}
    options))
  id)


(defn components []
  (registry/entities :ui-component))


(defn components-by-model-type [model-type]
  (registry/entities-by :ui-component :model-type model-type))
