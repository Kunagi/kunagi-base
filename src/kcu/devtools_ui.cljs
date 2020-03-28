(ns kcu.devtools-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.projector :as projector]
   [kcu.aggregator :as aggregator]))


(def color-command "#ffe0b2")
(def color-event "#bbdefb")
(def color-projection-step "#f5f5f5")
(def color-projection-value "#e1bee7")


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
      {:style {:display :grid
               :grid-template-columns "min-content auto"
               :grid-gap (theme/spacing 1)
               :align-items :baseline}}
      [:div
       {:style {:font-weight :bold
                :min-width "60px"
                :white-space :nowrap}}
       [muic/Data k]]
      [:div
       {:style {:min-width "100px"}}
       [muic/Data (get m k)]]])])


(defn Label [text]
  [:span
   {:style {:color :grey}}
   " " text " "])


(defn CommandCard [[command-name command-args]]
  [muic/Card
   {:style {:background-color color-command}}
   [:div
    {:style {:font-weight :bold}}
    command-name]
   [muic/Data command-args]])


(defn EventCard [event-name event-args]
  [muic/Card
   {:style {:background-color color-event}}
   [:div
    {:style {:font-weight :bold}}
    event-name]
   [muic/Data event-args]])

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
  (let [effects (-> step :effects)]
        ;; effects (dissoc effects :events)]
    [:div
     [Map-As-Stack effects]]))


(defn Aggregate-Step-Projections [step]
  (let [refs (-> step :aggregate :projections keys)]
    [:div
     (when-let [ex (get-in step [:projection-exception])]
       [muic/ExceptionCard ex])
     [muic/Stack-1
      (for [[projector entity :as ref] refs]
        ^{:key ref}
        [muic/Card
         {:style {:background-color color-projection-value}}
         [:div
          projector " " [:span {:style {:font-weight :bold}} entity]]])]]))


(defn Aggregate-Step-Projection [[projector-id entity-id :as ref] step]
  (let [projection (get-in step [:aggregate :projections ref])]
    [muic/Stack-1
     [:div
      {:style {:color (theme/color-primary-main)}}
      projector-id " "
      [:span {:style {:font-weight :bold}} entity-id]]
     [:div
      (for [pstep (-> projection :flow)]
        ^{:key (-> pstep :index)}
        [Projection-Step pstep])]]))


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
  (let [result (aggregator/simulate-commands aggregator commands)
        projection-refs (reduce (fn [refs step]
                                  (into refs (-> step :aggregate :projections keys)))
                                #{}
                                (get result :flow))]
    [muic/Stack-1
     (-> aggregator :id)
     [:div
      {:style {:overflow-x :auto}}
      [:table
       {:style {:height "1px"}}
       [:tbody
        [Aggregate-Command-Flow-Header "Command"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Command]
        [Aggregate-Command-Flow-Header "Used from Context"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Inputs]
        [Aggregate-Command-Flow-Header "Effects"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Effects]
        [Aggregate-Command-Flow-Header "Projections"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Projections]
        (for [ref (sort projection-refs)]
          ^{:key ref}
          [Aggregate-Command-Flow-Row result (partial Aggregate-Step-Projection ref)])]]]]))
