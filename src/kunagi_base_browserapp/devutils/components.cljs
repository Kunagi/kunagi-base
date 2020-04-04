(ns kunagi-base-browserapp.devutils.components
  (:require
   ["@material-ui/core" :as mui]
   [mui-commons.api :refer [<subscribe]]
   [mui-commons.components :as muic]))


(defn Aggregates []
  (let [aggregates (<subscribe [:event-sourcing/aggregates])]
    [muic/Data aggregates]))


(defn Projections []
  (let [projections (<subscribe [:event-sourcing/projections])]
    [muic/Data projections]))


(defn- TitledCard
  [title
   & components]
  [:> mui/Card
   (into
    [:> mui/CardContent
     [:h4 title]]
    components)])


(defn Toolbox []
  [:div
   [TitledCard
    "Projections"
    [Projections]]
   [:br] ;; TODO grid
   [TitledCard
    "Aggregates"
    [Aggregates]]])
