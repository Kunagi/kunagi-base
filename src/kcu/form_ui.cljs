(ns kcu.form-ui
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [kcu.utils :as u]))


(defn TextField [options]
 [:> mui/TextField
  options])
;; :value (or (bapp/subscribe checkin-ticket-nummer) "")
;; :on-change #(bapp/dispatch-reset checkin-ticket-nummer
;;                                  (-> % .-target .-value))
;; :on-key-down #(when (= 13 (-> % .-keyCode))
;;                 (rf/dispatch [:wartsapp/checkin-clicked]))
;; :error (boolean (-> schlange :checkin-fehler))
;; :helper-text (-> schlange :checkin-fehler)



(defn FormDialog [options])



(defn engage-dialog-trigger [trigger STATE]
  (when trigger
    (cond

      (and (vector? trigger)
           (= :> (first trigger))
           (map? (nth trigger 2)))
      (assoc-in trigger [2 :on-click] #(swap! STATE assoc :open? true))

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
                          (assoc :value (get options :value ""))))
        reset #(reset! STATE initial-state)]
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
           ;; [muic/Data state]
           (when-let [text (-> options :text)]
             [:> mui/DialogContentText text])
           [:> mui/TextField
            (merge {:auto-focus true
                    :margin :dense
                    :full-width true
                    :value (-> state :value)
                    :on-change #(swap! STATE assoc :value (-> % .-target .-value))
                    :on-key-down #(when (= 13 (-> % .-keyCode))
                                    (submit))}
                   (-> options :text-field)
                   {:disabled blocked?
                    :error (boolean error-text)
                    :helper-text error-text})]]
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
