(ns kcu.simulator-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.utils :as u]
   [kcu.bapp :as bapp]
   [kcu.system :as system]
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
(def color-result "#81c784")
(def color-rejection "#e57373")
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

(defn CommandCard [command]
  [muic/Card
   {:style {:background-color color-command}}
   [muic/Stack-1
    [:div.b (-> command :command/name str)]
    [Map-As-Stack (dissoc command
                          :command/name
                          :command/id
                          :command/time)]]])


(defn EventCard [event]
  [muic/Card
   {:style {:background-color color-event}}
   [muic/Stack-1
    [:div.b (-> event :event/name str)]
    [Map-As-Stack (dissoc event :event/name
                          :event/id
                          :event/time
                          :aggregate/aggregator
                          :aggregate/id
                          :aggregate/tx-num
                          :aggregate/tx-id
                          :aggregate/tx-time)]]])


(defn ProjectionDataCard [projection]
  [muic/Card
   {:style {:background-color color-projection}}
   [Map-As-Stack projection]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn TxCommand [tx]
  (let [command (-> tx :command)]
    [muic/Stack-1
     ;; [muic/Data (->> step :projection-results vals)]
     [CommandCard command]
     ;; FIXME exceptions / errors
     ;; [muic/Data (-> tx :keys)]
     (when-let [exception (-> tx :exception)]
       [muic/ExceptionCard exception])
     (when-let [exception (-> tx :command-exception)]
       [muic/ExceptionCard exception])
     (when-let [ex (-> tx :events-exception)]
       [muic/ExceptionCard ex])
     (when-let [ex (-> tx :projection-exception)]
       [muic/ExceptionCard ex])]))


(defn TxEffects [tx]
  (let [effects (-> tx :effects)]
    [muic/Stack-1
     (for [effect effects]
       (cond

         (aggregator/event? effect)
         ^{:key effect}
         [EventCard effect]

         :else
         ^{:key effect}
         [muic/Card
          {:style {:background-color (case (-> effect :effect/type)
                                       :result color-result
                                       :rejection color-rejection
                                       color-unknown)}}
          [muic/Data effect]]))]))


(defn TxAggregate [tx]
  (let [aggregate (-> tx :aggregate)]
    [muic/Stack-1
     (for [event (-> tx :applied-events)]
       ^{:key (-> event :event/id)}
       [EventCard event])
     [muic/Card
      {:style {:background-color color-aggregate}}
      [Map-As-Stack (dissoc aggregate
                            :aggregate/tx-num
                            :aggregate/tx-id
                            :aggregate/tx-time
                            :aggregate/aggregator
                            :aggregate/id)]]]))

(defn TxInputs [tx]
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

         (-> tx :inputs)))])


(defn- projection-by-id [tx projection-id]
  (->> tx
       :projections
       vals
       (filter #(= projection-id (get % :projection/id)))
       first))


(defn TxProjection [projection-id tx]
  (let [projection (projection-by-id tx projection-id)]
    (when projection
      [muic/Stack-1
       [:div
        [:span.b (-> projection :projection/projector) " "]
        [:span.monospace projection-id]]
       (for [event (->> tx
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

(defn TxUiComponent [[component projection-id] tx]
  (let [projection (projection-by-id tx projection-id)]
    (when (and projection (= (-> component :model-type)
                             (-> (projector/projector
                                  (-> projection :projection/projector))
                                 :type)))
      [muic/Stack-1
       [muic/Card
        {:style {:background-color color-projection}}
        (-> projection :projection/projector str)
        " "
        (-> projection :projection/id str)]
       [muic/Card
        {:style {:background-color color-ui}}
        [muic/ErrorBoundary
         [(-> component :f) projection]]]])))


(defn Row
  [system component-f]
  (into
   [:tr]
   (map (fn [tx]
          [:td
           [muic/Card
            {:style {:height "100%"}}
            [component-f tx]]])
        (-> system system/transactions))))


(defn HeaderRow [text]
  [:tr
   [:td
    {:style {:padding-top (theme/spacing 2)}}
    text]])


(defn- projection-ids-by-type [system model-type]
  (->> system
       system/loaded-projections
       (filter #(= model-type (get % :projection/type)))
       (map :projection/id)
       (into [])))


(defn Test [& args]
  [:div (str args)])


(defn uics-and-projections [system]
  (let [ui-components (reduce
                        (fn [ret uic]
                          (let [model-type (-> uic :model-type)
                                projectors (projector/projectors-by-type
                                            model-type)]
                            (into ret (map #(assoc uic :projector %))
                                  projectors)))
                        []
                        (bapp/components))]
    (reduce (fn [ret uic]
              (into
               ret
               (reduce (fn [ret projection-id]
                         (conj ret [uic projection-id]))
                       [] (projection-ids-by-type system (-> uic :model-type)))))
            [] ui-components)))


(defn CommandsFlow
  [flow]
  (let [commands (-> flow :commands)
        system (system/new-system :simulator {})

        ;; _ (system/dispatch-command system
        ;;                            {:command/name :wartsapp/ziehe-nummer
        ;;                             :patient/id "patient-X"}
        ;;                            #(js/alert %))

        _ (system/dispatch-commands system commands)

        errors (system/errors system)

        projection-ids (->> system
                            system/loaded-projections
                            (map :projection/id))]
    [muic/Stack-1
     ;; [muic/Card [muic/Data (uics-and-projections system)]]
     (for [error errors]
       ^{:key error}
       [muic/ErrorCard
        [muic/Data error]])
     [:div {:style {:overflow-x :auto}}
      [:table {:style {:height "1px"}}
       [:tbody
        [HeaderRow "Command"]
        [Row system TxCommand]

        [HeaderRow "Effects"]
        [Row system TxEffects]

        [HeaderRow "Aggregate State"]
        [Row system TxAggregate]

        [HeaderRow "Used from Context"]
        [Row system TxInputs]

        [HeaderRow "Projections"]
        (for [id (sort projection-ids)]
          ^{:key id}
          [Row system (partial TxProjection id)])

        [HeaderRow "User Interface Components"]
        (for [ui-and-projection (uics-and-projections system)]
          ^{:key ui-and-projection}
          [Row system (partial TxUiComponent ui-and-projection)])]]]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- CommandFlowLink [flow]
  [muic/ActionCard
   {:href (str "simulator?flow=" (-> flow :id))}
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
       [CommandsFlow flow])]))
