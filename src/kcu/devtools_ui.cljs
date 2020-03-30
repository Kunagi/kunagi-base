(ns kcu.devtools-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.projector :as projector]
   [kcu.aggregator :as aggregator]))


(def color-command "#bbdefb")
(def color-event "#ffe0b2")
(def color-projection "#e1bee7")
(def color-projection-step "#f5f5f5")
(def color-context "#c8e6c9")

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
      [:div.b
       {:style {:min-width "60px"
                :white-space :nowrap}}
       [muic/Data k]]
      [:div
       {:style {:min-width "100px"}}
       [muic/Data (get m k)]]])])


(defn Label [text]
  [:span
   {:style {:color :grey}}
   " " text " "])


(defn ProjectionRefText [[projector entity]]
  [:div
   [:span.b projector]
   " "
   entity])

(defn CommandCard [[command-name command-args]]
  [muic/Card
   {:style {:background-color color-command}}
   [muic/Stack-1
    [:div.b command-name]
    [Map-As-Stack command-args]]])


(defn EventCard [event-name event-args]
  [muic/Card
   {:style {:background-color color-event}}
   [muic/Stack-1
    [:div.b event-name]
    [Map-As-Stack event-args]]])


(defn ProjectionDataCard [projection]
  [muic/Card
   {:style {:background-color color-projection}}
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
  (let [projection (projector/new-projection projector nil)
        projection-result (projector/project projector projection events)]
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
     ;; [muic/Data (->> step :projection-results vals)]
     [CommandCard command]
     (when-let [exception (-> step :command-exception)]
       [muic/ExceptionCard exception])
     (when-let [ex (-> step :projection-exception)]
       [muic/ExceptionCard ex])]))


(defn Aggregate-Step-Events [step]
  (let [event-sets (-> step :effects :events)]
    [muic/Stack
     {:spacing (theme/spacing 2)}
     (for [[projection-ref events] event-sets]
       ^{:key projection-ref}
       [muic/Stack
        [muic/Card
         {:style {:background-color color-projection}}
         [ProjectionRefText projection-ref]]
        (for [[event-name args :as event] events]
          ^{:key event}
          [EventCard event-name args])])]))


(defn Aggregate-Step-DomainEvents [step]
  (let [events (-> step :effects :devents)]
    [muic/Stack
     {:spacing (theme/spacing 2)}
     (for [event events]
       ^{:key event}
       [muic/Card
        {:style {:background-color color-event}}
        [muic/Data event]])]))


(defn Aggregate-Step-Inputs [step]
  [:div
   (into
    [muic/Stack-1]
    (map (fn [input]
           (case (if (vector? input)
                   (first input)
                   input)

             :projection
             [muic/Card
              {:style {:background-color color-projection}}
              [ProjectionRefText (second input)]]

             [muic/Card
              {:style {:background-color color-context}}
              [muic/Data input]]))

         (-> step :inputs)))])


(defn Aggregate-Step-Effects [step]
  (let [effects (-> step :effects)
        effects (dissoc effects :events)]
    [:div
     [Map-As-Stack effects]]))


(defn Aggregate-Step-Projection [[projector-id entity-id :as ref] step]
  (let [flow (get-in step [:projection-results ref :flow])]
    [muic/Stack-1
     [:div
      {:style {:color (theme/color-primary-main)}}
      projector-id " "
      [:span.b entity-id]]
     [:div
      (for [pstep flow]
        ^{:key (-> pstep :index)}
        [Projection-Step pstep])]]))


(defn Aggregate-Command-Flow-Row
  [result component-f]
  [:tr
   (for [step (-> result :flow)]
     ^{:key (-> step :index)}
     [:td
      [muic/Card
       {:style {:height "100%"
                :min-width "300px"}}
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
                                  (into refs (-> step :projection-results keys)))
                                #{}
                                (get result :flow))]
    [muic/Stack-1
     [:div {:style {:overflow-x :auto}}
      [:table {:style {:height "1px"}}
       [:tbody
        [Aggregate-Command-Flow-Header "Command"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Command]

        [Aggregate-Command-Flow-Header "Domain Events"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-DomainEvents]

        [Aggregate-Command-Flow-Header "Used from Context"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Inputs]

        [Aggregate-Command-Flow-Header "Effects"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Effects]

        [Aggregate-Command-Flow-Header "Events"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Events]

        [Aggregate-Command-Flow-Header "Projections"]
        (for [ref (sort projection-refs)]
          ^{:key ref}
          [Aggregate-Command-Flow-Row result (partial Aggregate-Step-Projection ref)])]]]]))
