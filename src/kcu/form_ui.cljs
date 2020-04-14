(ns kcu.form-ui
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [kcu.utils :as u]))


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


(defn TextFieldDialog [options]
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
                  (on-close STATE)))]
    (fn [options]
      (let [state @STATE
            blocked? (-> state :blocked?)
            error-text (-> state :error-text)
            trigger (or (engage-dialog-trigger (-> options :trigger)
                                               STATE)
                        (engage-dialog-trigger [:> mui/Button {}
                                                (-> options :title)]
                                               STATE))
            on-submit (-> options :on-submit)
            submit #(when (on-submit
                           STATE
                           {:close reset
                            :block (fn [] (swap! STATE assoc :blocked? true))
                            :unblock (fn [options]
                                       (swap! STATE merge
                                              options {:blocked? false}))})
                      (reset))]
        [:div
         (when trigger
           trigger)
         [:> mui/Dialog
          {:open (-> state :open?)
           :on-close reset}
          (when-let [title (-> options :title)]
            [:> mui/DialogTitle title])
          [:> mui/DialogContent
           ;; [muic/DataCard state]
           (when-let [text (-> options :text)]
             [:> mui/DialogContentText text])
           ;; [muic/DataCard state]
           [:> mui/TextField
            (merge {:auto-focus true
                    :margin :dense
                    :full-width true
                    :value (or (-> state :value) "")
                    :on-change #(swap! STATE assoc :value (-> % .-target .-value))
                    :on-key-down #(when (= 13 (-> % .-keyCode))
                                    (submit))}
                   (-> options :text-field)
                   {:disabled blocked?
                    :error (boolean error-text)
                    :helper-text error-text})]]
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
             :on-click submit}
            (get options :submit-button-text "Submit")]]]]))))


(defn show-editor-dialog [editor]
  (let [dialog-id (u/current-time-millis)
        on-close (fn [_STATE]
                   (u/invoke-later!
                    1000
                    #(swap! DIALOGS dissoc dialog-id)))
        component (case (-> editor :type)

                    :text-1 [TextFieldDialog (-> editor
                                                 (assoc :trigger :auto-open)
                                                 (assoc :dialog-id dialog-id)
                                                 (assoc :on-close on-close))]
                    (throw (ex-info (str "Unsupported editor type `"
                                         (-> editor :type) "`.")
                                    {:editor editor})))]
    (swap! DIALOGS assoc dialog-id component)))
