(ns kunagi-base-browserapp.modules.devtools.tap
  (:require
   [reagent.core :as r]
   [kcu.tap :as logging-tap]
   [mui-commons.components :as muic]))


(defonce !records (r/atom '()))


(def card-colors
  {:err "#ff7043"
   :wrn "#ffa726"
   :inf "#d4e157"})

(defn Record [record]
  [muic/Card
   {:style {:background-color (-> record :level card-colors)}}
   [muic/Stack
    [:div
     [:span (-> record :level)]
     " "
     [:span (-> record :source-ns)]
     " "
     [:strong (-> record :source-name)]]
    (when-let [payload (-> record :payload)]
      [muic/Data payload])]])


(defn Records []

  [muic/Stack
   {:items (take 10 @!records)
    :template [Record]}])


(defn Workarea []
  [Records])


(defn init-tap! [db]
  (add-tap #(swap! !records conj (logging-tap/o->record %)))
  db)

