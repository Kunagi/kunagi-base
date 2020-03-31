(ns kcu.aggregator-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.ui :as ui]
   [kcu.registry :as registry]
   [kcu.projector :as projector]
   [kcu.aggregator :as aggregator]))


(def color-unknown "#cfd8dc")
(def color-command "#bbdefb")
(def color-event "#ffe0b2")
(def color-aggregate "#ffccbc")
(def color-projection "#e1bee7")
(def color-projection-step "#f5f5f5")
(def color-context "#c8e6c9")
(def color-ui color-unknown)


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
       {:style {:min-width "100px"
                :white-space :nowrap}}
       [muic/Data k]]
      [:div
       {:style {:min-width "200px"}}
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
    [:div.b (-> event :event/name str)]
    [Map-As-Stack (dissoc event :event/name :event/id :event/time)]]])


(defn ProjectionDataCard [projection]
  [muic/Card
   {:style {:background-color color-projection}}
   [Map-As-Stack projection]])

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


(defn Aggregate-Step-Projection [projection-id step]
  (let [projection (->> step
                        :projections
                        (filter #(= projection-id (get % :projection/id)))
                        first)]
    (when projection
      [muic/Stack-1
       [:div
        [:span.b (-> projection :projection/projector) " "]
        [:span.monospace projection-id]]
       (for [event (->> step
                        :effects
                        (filter aggregator/event?)
                        (filter #(contains? (-> projection :projection/handled-events)
                                            (-> % :event/id))))]
         ^{:key (-> event :event/id)}
         [EventCard event])
       [muic/Card
        {:style {:background-color color-projection}}
        [muic/Stack-1
         [Map-As-Stack (dissoc projection
                               :projection/projector
                               :projection/id
                               :projection/handled-events
                               :projection/type)]]]])))


(defn Aggregate-Step-UiComponent [component step]
  (let [projections (->> step
                         :projections
                         (filter #(= (get component :model-type)
                                     (get % :projection/type))))]
    [muic/Stack-1
     ;; [:span.monospace (-> component :id)]
     (for [projection projections]
       ^{:key (-> projection :id)}
       [muic/Stack-1
        [muic/Card
         {:style {:background-color color-projection}}
         (-> projection :projection/projector str)
         " "
         (-> projection :projection/id str)]
        [muic/Card
         {:style {:background-color color-ui}}
         [muic/ErrorBoundary
          [(-> component :f) projection]]]])]))


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


(defn Test-Flow
  [flow]
  (let [aggregator (aggregator/aggregator (-> flow :aggregator))
        commands (-> flow :commands)
        projectors (projector/projectors)
        ui-components (reduce
                       (fn [ret uic]
                         (let [model-type (-> uic :model-type)
                               projectors (projector/projectors-by-type
                                           model-type)]
                           (into ret (map #(assoc uic :projector %))
                                     projectors)))
                       []
                       (ui/components))
        result (aggregator/simulate-commands aggregator commands projectors)
        projection-ids (reduce (fn [ids step]
                                 (into ids (->> step
                                                :projections
                                                (map #(get % :projection/id)))))
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
        [Aggregate-Command-Flow-Row result Aggregate-Step-State]

        [Aggregate-Command-Flow-Header "Used from Context"]
        [Aggregate-Command-Flow-Row result Aggregate-Step-Inputs]

        [Aggregate-Command-Flow-Header "Projections"]
        (for [id (sort projection-ids)]
          ^{:key id}
          [Aggregate-Command-Flow-Row result (partial Aggregate-Step-Projection id)])

        [Aggregate-Command-Flow-Header "User Interface Components"]
        (for [uic ui-components]
          ^{:key [(-> uic :id) (-> uic :projector :id)]}
          [Aggregate-Command-Flow-Row result (partial Aggregate-Step-UiComponent uic)])]]]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- CommandFlowLink [flow]
  [muic/ActionCard
   {:href (str "aggregators?flow=" (-> flow :id))}
   (-> flow :name name (.split "-") (.join " "))])


(defn Workarea [{flow-id :flow}]
  (let [flows (registry/entities :command-test-flow)
        flow-id (u/decode-edn flow-id)
        flow (when flow-id
               (->> flows
                    (filter #(= flow-id (-> % :id)))
                    first))]
    [muic/Stack-1
     (if-not flow
       [muic/Stack-1
        (for [[aggregator flows] (group-by #(get % :aggregator) flows)]
          ^{:key aggregator}
          [muic/Stack-1
           [:div aggregator]
           [muic/Inline
            {:items flows
             :template [CommandFlowLink]}]])]
       [Test-Flow flow])]))
