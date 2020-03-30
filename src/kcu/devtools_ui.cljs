(ns kcu.devtools-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.projector :as projector]
   [kcu.aggregator :as aggregator]))


(def color-unknown "#cfd8dc")
(def color-command "#bbdefb")
(def color-event "#ffe0b2")
(def color-aggregate "#ffccbc")
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


(defn EventCard [event]
  [muic/Card
   {:style {:background-color color-event}}
   [muic/Stack-1
    [:div.b (-> event :event/name)]
    [Map-As-Stack (dissoc event :event/name :event/id :event/time)]]])


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
     (when-let [ex (-> step :events-exception)]
       [muic/ExceptionCard ex])
     (when-let [ex (-> step :projection-exception)]
       [muic/ExceptionCard ex])]))


(defn Aggregate-Step-Effects [step]
  (let [effects (-> step :effects)]
    [muic/Stack-1
     (for [effect effects]
       (cond

         (aggregator/event? effect)
         ^{:key effect}
         [EventCard effect]

         :else
         ^{:key effect}
         [muic/Card
          {:style {:background-color color-unknown}}
          [muic/Data effect]]))]))


(defn Aggregate-Step-State [step]
  (let [aggregate (-> step :aggregate)]
    [muic/Stack-1
     (for [event (-> step :applied-events)]
       ^{:key (-> event :event/id)}
       [EventCard event])
     [muic/Card
      {:style {:background-color color-aggregate}}
      [Map-As-Stack aggregate]]]))


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

        [Aggregate-Command-Flow-Header "Effects"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Effects]

        [Aggregate-Command-Flow-Header "Aggregate State"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-State]]]]]))

        ;; [Aggregate-Command-Flow-Header "Used from Context"]
        ;; [Aggregate-Command-Flow-Row result Aggregate-Step-Inputs]

        ;; [Aggregate-Command-Flow-Header "Events"]
        ;; [Aggregate-Command-Flow-Row result Aggregate-Step-Events]

        ;; [Aggregate-Command-Flow-Header "Projections"]
        ;; (for [ref (sort projection-refs)]
        ;;   ^{:key ref}
        ;;   [Aggregate-Command-Flow-Row result (partial Aggregate-Step-Projection ref)])]]]]))
