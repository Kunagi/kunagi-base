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
     [muic/Inline
      [:div {:style {:color :gery}} k]
      [muic/Data (get m k)]])])


(defn Label [text]
  [:span
   {:style {:color :grey}}
   " " text " "])

(def color-command "#ffe0b2")

(defn CommandCard [[command-name command-args]]
  [muic/Card
   {:style {:background-color color-command}}
   [:div
    {:style {:font-weight :bold}}
    command-name]
   [muic/Data command-args]])


(def color-event "#bbdefb")

(defn EventCard [event-name event-args]
  [muic/Card
   {:style {:background-color color-event}}
   [:div
    {:style {:font-weight :bold}}
    event-name]
   [muic/Data event-args]])

(def color-projection "#f5f5f5")
(def color-projection-step "#ffecb3")
(def color-projection-value "#e1bee7")

(defn ProjectionDataCard [projection]
  [muic/Card
   {:style {:background-color color-projection-value}}
   [Map-As-Stack projection]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Projection-Step
  [{:keys [event projection]}]
  (let [[event-name event-args] event
        projection (dissoc projection
                           :projection/projector)]
    [muic/Card
     {:style {:background-color color-projection-step}}
     [muic/Stack-1
      [EventCard event-name event-args]
      [ProjectionDataCard projection]]]))


(defn Projection-Event-Flow
  [{:keys [projector events]}]
  (let [projection-result (projector/project projector events)]
    [muic/Stack-1
     [:div
      [Label "Projection Event Flow"]
      (-> projector :id)]
     [Sidescroller
      (for [step (-> projection-result :flow)]
        ^{:key (-> step :index)}
        [Projection-Step step])]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Aggregate-Step-Command [step]
  (let [command (-> step :command)]
    [muic/Stack-1
     [CommandCard command]
     (when-let [exception (-> step :command-exception)]
       [muic/ExceptionCard exception])]))


(defn Aggregate-Step-Inputs [step]
  [:div
   (into
    [muic/Stack]
    (map (fn [input] [muic/Data input])
         (-> step :inputs)))])


(defn Aggregate-Step-Effects [step]
  (let [effects (-> step :effects)
        effects (dissoc effects :events)]
    [:div
     [Map-As-Stack effects]]))


(defn Aggregate-Step-Projections [step]
  [muic/Stack
   {:spacing (theme/spacing 4)}
   (when-let [ex (-> step :projection-exception)]
     [muic/ExceptionCard ex])
   (let [projections (-> step :aggregate :projections)]
     (for [k (->> projections keys sort)]
       (let [projection (get projections k)]
         ^{:key (-> k)}
         [muic/Card
          {:style {:background-color color-projection}}
          [muic/Stack-1
           [:div
            {:style {:color (theme/color-primary-main)}}
            (first k) " " (second k)
            [Label "projection"]]
           [:div
            (for [pstep (-> projection :flow)]
              ^{:key (-> pstep :index)}
              [Projection-Step pstep])]]])))])


(defn Aggregate-Command-Flow-Row
  [result component-f]
  [:tr
   (for [step (-> result :flow)]
     ^{:key (-> step :index)}
     [:td
      [muic/Card
       {:style {:height "100%"}}
       [component-f step]]])])

(defn Aggregate-Command-Flow-Header [text]
  [:tr
   [:td
    {:style {:padding-top (theme/spacing 2)}}
    text]])


(defn Aggregate-Command-Flow
  [{:keys [aggregator commands]}]
  (let [result (aggregator/simulate-commands aggregator commands)]
    [muic/Stack-1
     (-> aggregator :id)
     [:div
      {:style {:overflow-x :auto}}
      [:table
       {:style {:height "1px"}}
       [Aggregate-Command-Flow-Header "Command"]
       [Aggregate-Command-Flow-Row result Aggregate-Step-Command]
       [Aggregate-Command-Flow-Header "Used from Context"]
       [Aggregate-Command-Flow-Row result Aggregate-Step-Inputs]
       [Aggregate-Command-Flow-Header "Effects"]
       [Aggregate-Command-Flow-Row result Aggregate-Step-Effects]
       [Aggregate-Command-Flow-Header "Projections"]
       [Aggregate-Command-Flow-Row result Aggregate-Step-Projections]]]]))
