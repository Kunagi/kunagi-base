(ns kcu.form-ui
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [kcu.utils :as u]))




;;; Editor Field ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defmulti editor-field (fn [options] (or (get options :type) :text-1)))


(defn- mui-text-field-editor-field
  [{:as options
    :keys [value submit-f disabled?
           error-text]}
   special-options]
  (let [id (str "editor-field-" (random-uuid))]
    [(fn [] (-> (js/document.getElementById id) .-value))
     (muic/with-css
       {"& textarea" {:font-family (when (get special-options :monospace?) :mono)}}
       [:> mui/TextField
        (merge
         {:id id
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
         (dissoc special-options :monospace?))])]))


(defmethod editor-field :text-1
  [options]
  (mui-text-field-editor-field options {}))


(defmethod editor-field :text-n
  [options]
  (mui-text-field-editor-field
   options {:multiline true
            :rows 20}))


(defmethod editor-field :code
  [options]
  (mui-text-field-editor-field
   options {:multiline true
            :rows 20
            :monospace? true}))

;;; Editor Dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def DIALOGS (r/atom {}))


(defn CommonDialogsContainer []
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
            [value-getter Field] (editor-field
                                  {:type (-> options :type)
                                   :value (-> options :value)
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


(defn show-editor-dialog [editor]
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
