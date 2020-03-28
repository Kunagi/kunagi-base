(ns kcu.devtools-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.projector :as projector]
   [kcu.aggregator :as aggregator]))


(defn Sidescroller
  [elements]
  [:div
   {:style {:overflow-x :auto}}
   (into
    [:div
     {:style {:display :grid
              :grid-template-columns (str "repeat(" (count elements) ", 1fr)")
              :grid-gap (theme/spacing 1)}}]
    elements)])


(defn Map-As-Stack [m]
  [muic/Stack
   (for [k (->> m keys sort)]
     ^{:key k}
     [:div
      [:div
       {:style {:color (theme/color-primary-main)}}
       k]
      [muic/Data (get m k)]])])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Projection-Step
  [{:keys [event projection]}]
  (let [[event-name event-args] event
        projection (dissoc projection
                           :projection/projector)]
    [muic/Card
     [muic/Stack-1
      [:div
       {:style {:font-weight :bold}}
       event-name]
      [muic/Data event-args]
      [:> mui/Divider]
      [Map-As-Stack projection]]]))


(defn Projection-Event-Flow
  [{:keys [projector events]}]
  (let [projection-result (projector/project projector events)]
    [muic/Stack-1
     (-> projector :id)
     [Sidescroller
      (for [step (-> projection-result :flow)]
        ^{:key (-> step :index)}
        [Projection-Step step])]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Aggregate-Step
  [{:keys [command effects inputs]}]
  (let [[command-name command-args] command]
    [muic/Card
     [muic/Stack-1
      [:div
       {:style {:font-weight :bold}}
       command-name]
      [muic/Data command-args]

      [:> mui/Divider]
      (into
       [muic/Stack]
       (map (fn [input] [muic/Data input])
            inputs))

      [:> mui/Divider]
      [Map-As-Stack effects]]]))


(defn Aggregate-Command-Flow
  [{:keys [aggregator commands]}]
  (let [aggregate-result (aggregator/simulate-commands aggregator commands)]
    [muic/Stack-1
     (-> aggregator :id)
     [Sidescroller
      (for [step (-> aggregate-result :flow)]
        ^{:key (-> step :index)}
        [Aggregate-Step step])]]))
