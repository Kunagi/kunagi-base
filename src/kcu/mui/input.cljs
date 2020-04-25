(ns kcu.mui.input
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [kcu.devcards :refer [devcard]]
   [kcu.utils :as u]
   [kcu.mui.table :as table]))


;;; TextField ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn text
  [{:as options
    :keys [value submit-f disabled?
           value-field-id error-text]}
   special-options]
  [(fn [] (-> (js/document.getElementById value-field-id) .-value))
   (muic/with-css
     {"& textarea" {:font-family (when (get special-options :monospace?)
                                   :monospace)}}
     [:> mui/TextField
      (merge
       {:id value-field-id
        :auto-focus true
        :margin :dense
        :full-width true
        :default-value value
        :on-key-down (when-not (-> special-options :rows)
                       #(when (= 13 (-> % .-keyCode))
                          (submit-f (-> % .-target .-value))))
        :disabled disabled?
        :error (boolean error-text)
        :helper-text error-text}
       (dissoc special-options :monospace?))])])



;;; Select-1 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn select-1
  [{:as options
    :keys [value options-value-key options-columns options]}]
  (let [x nil]
    [(fn [] "FIXME")
     [table/Table
      {:cols (map (fn [col]
                    (-> col))
                  options-columns)}
      options]]))


;;; dispatcher ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn field
  [options]
  (case (get options :type)

    :text-n (text options
                  {:multiline true
                   :rows 20})

    :text-1 (text options {})

    :code (text options
                {:multiline true
                 :rows 20
                 :monospace? true})

    :select-1 (select-1 options)

    (text options {})))


(defn- DevcardEditorComponent [editor]
  (let [[_getter component] (field editor)]
    component))


(devcard
 ::select-1
 [DevcardEditorComponent {:type :select-1
                          :value :witek
                          :options-value-key :id
                          :options-columns [{:label "Name"
                                             :key :name
                                             :type :text-1}
                                            {:label "Age"
                                             :key :age}]
                          :options [{:id :kacper
                                     :name "Kacper"
                                     :age 37}
                                    {:id :witek
                                     :name "Witek"
                                     :age 40}]}])


(devcard
 ::text-1
 [DevcardEditorComponent {:type :text-1
                          :value "Hello World"}])

(devcard
 ::text-n
 [DevcardEditorComponent {:type :text-n
                          :value "Hello\nWorld"}])

(devcard
 ::code
 [DevcardEditorComponent {:type :code
                          :value "{:hello \"World\"}"}])


;;; Dialogs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def DIALOGS (r/atom {}))


(defn DialogsContainer []
  (into
   [:div.DialogsContainer]
   ;; [muic/DataCard (-> @DIALOGS vals)]]
   (-> @DIALOGS vals)))


(defn- engage-dialog-trigger [trigger STATE]
  (when trigger
    (cond

      (= trigger :auto-open)
      [:span.DialogTrigger]

      ;; react component like [:> Button {} "..."]
      (and (vector? trigger)
           (= :> (first trigger))
           (map? (nth trigger 2)))
      (assoc-in trigger [2 :on-click] #(swap! STATE assoc :open? true))

      ;; reagent component like [Button {} "..."]
      (and (vector? trigger)
           (map? (second trigger)))
      (assoc-in trigger [1 :on-click] #(swap! STATE assoc :open? true))

      :else (throw (ex-info (str "Unsupported trigger component. "
                                 "Must be like [:> mui/Button {} ...]")
                            {})))))


(defn EditorDialog [options]
  ;; FIXME focus after unblock
  (let [initial-state {:open? false
                       :value ""}
        STATE (r/atom (-> initial-state
                          (assoc :open? (if (= :auto-open (get-in options [:trigger]))
                                          true
                                          false))
                          (assoc :value (get options :value ""))))
        value-field-id (str "editor-dialog-field-" (random-uuid))
        value-getter (fn []
                       (-> (js/document.getElementById value-field-id) .-value))
        reset (fn []
                (reset! STATE initial-state)
                (when-let [on-close (get options :on-close)]
                  (on-close STATE)))
        block (fn [] (swap! STATE assoc :blocked? true))
        unblock (fn [options]
                  (swap! STATE merge
                         options {:blocked? false}))
        submit #(when ((-> options :on-submit)
                       %
                       {:close reset
                        :block block
                        :unblock unblock})
                  (reset))]
    (fn [options]
      (let [state @STATE
            blocked? (-> state :blocked?)
            error-text (-> state :error-text)
            [_alue-getter Field] (field
                                  {:type (-> options :type)
                                   :value (-> options :value)
                                   :value-field-id value-field-id
                                   ;; :on-change #(swap! STATE assoc :value %)
                                   :submit-f submit
                                   :disabled? blocked?})]
        [:div
         (when-let [trigger (or (engage-dialog-trigger (-> options :trigger)
                                                       STATE)
                                (engage-dialog-trigger [:> mui/Button {}
                                                        (-> options :title)]
                                                       STATE))]
           trigger)
         [:> mui/Dialog
          {:open (-> state :open?)
           :full-width true
           :max-width :xl
           :on-close reset}
          (when-let [title (-> options :title)]
            [:> mui/DialogTitle title])
          [:> mui/DialogContent
           ;; [muic/DataCard options]
           ;; [muic/DataCard state]
           (when-let [text (-> options :text)]
             [:> mui/DialogContentText text])

           (when error-text
             [:div
              {:style {:color :red}}
              error-text])

           Field]

          (when blocked?
           [:> mui/LinearProgress
            {:color :secondary}])
          [:> mui/DialogActions
           [:> mui/Button
            {:on-click reset
             :color :primary}
            (get options :cancel-button-text "Cancel")]
           [:> mui/Button
            {:color :primary
             :variant :contained
             :disabled blocked?
             :on-click #(submit (value-getter))}
            (get options :submit-button-text "Submit")]]]]))))


(defn show-dialog [editor]
  (let [dialog-id (u/current-time-millis)
        on-close (fn [_STATE]
                   (u/invoke-later!
                    1000
                    #(swap! DIALOGS dissoc dialog-id)))
        component [EditorDialog (-> editor
                                    (assoc :trigger :auto-open)
                                    (assoc :dialog-id dialog-id)
                                    (assoc :on-close on-close))]]
    (swap! DIALOGS assoc dialog-id component)))


(devcard
 ::dialogs
 [muic/Inline
  [:> mui/Button
   {:on-click #(show-dialog
                {})}
   "Dialog"]])
