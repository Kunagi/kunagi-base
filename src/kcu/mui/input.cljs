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


(defn Text
  [{:as options
    :keys [value submit-f disabled?
           value-field-id error-text
           value-is-edn?]}
   special-options]
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
       :helper-text error-text
       :style {:min-width (when (get special-options :multiline)
                            "300px")}}
      (when value-is-edn?
        {:input-props {"data-value-is-edn" "true"}})
      (dissoc special-options :monospace?))]))


(defn Text-n [options]
  (Text options
        {:multiline true
         :rows 20}))


(defn Code [options]
  (Text options
        {:multiline true
         :rows 20
         :monospace? true}))


(defn EdnAsText [options]
  (let [value (u/encode-edn (get options :value))]
    (Text (assoc options
                 :value value
                 :value-is-edn? true)
          {:multiline true
           :rows 20
           :monospace? true})))


;;; Select-1 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Select-1
  [{:keys [value options-value-key select-cols select-options value-field-id]}]
  [:div
   ;; [muic/DataCard :select-options select-options]
   [table/Table
    {:selection-mode :one
     :selection-input-id value-field-id
     :selected value
     :cols (map (fn [col]
                  (-> col))
                select-cols)}
    select-options]])


;;; dispatcher ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Field
  [options]
  (case (get options :type)
    :text-n (Text-n options)
    :text-1 (Text options {})
    :code (Code options)
    :edn (EdnAsText options)
    :select-1 (Select-1 options)
    (Text options {})))


(devcard
 ::select-1
 (Field {:type :select-1
         :value :witek
         :options-value-key :id
         :select-cols [{:label "Name"
                        :key :name
                        :type :text-1}
                       {:label "Age"
                        :key :age}]
         :select-options [{:id :kacper
                           :name "Kacper"
                           :age 37}
                          {:id :witek
                           :name "Witek"
                           :age 40}]}))


(devcard
 ::text-1
 (Field {:type :text-1
         :value "Hello World"}))

(devcard
 ::text-n
 (Field {:type :text-n
         :value "Hello\nWorld"}))

(devcard
 ::code
 (Field {:type :code
         :value "{:hello \"World\"}"}))

(devcard
 ::edn
 (Field {:type :edn
         :value {:hello ["world"]
                 :of #{:clojure 42}}}))

;;; Dialogs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def DIALOGS (r/atom {}))


(defn DialogContainer [dialog]
  (js/console.log "painting dialog" dialog)
  [:div
   dialog])


(defn DialogsContainer []
  [:div.DialogsContainer
   ;; [muic/DataCard (-> @DIALOGS vals)]
   (for [[id dialog] (-> @DIALOGS)]
     ^{:key id}
     [DialogContainer dialog])])


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
                       (let [e (js/document.getElementById value-field-id)
                             edn? (-> e (.getAttribute "data-value-is-edn"))
                             value (-> e .-value)]
                         (if edn?
                           (u/decode-edn value)
                           value)))
        reset (fn []
                (reset! STATE initial-state)
                (when-let [on-close (get options :on-close)]
                  (on-close STATE)))
        block (fn [] (swap! STATE assoc :blocked? true))
        unblock (fn [options]
                  (swap! STATE merge
                         options {:blocked? false}))
        submit (fn [value]
                 (let [on-submit (-> options :on-submit)
                       close? (if-not on-submit
                                 true
                                 (on-submit value
                                            {:close reset
                                             :block block
                                             :unblock unblock}))]
                   (when close?
                     (when-let [on-submitted (-> options :on-submitted)]
                       (u/invoke-later! 100 #(on-submitted value)))
                     (reset))))]
    (fn [options]
      (let [state @STATE
            blocked? (-> state :blocked?)
            error-text (-> state :error-text)
            Field (Field
                   (-> options
                       (assoc :type (-> options :type)
                              :value (-> options :value)
                              :value-field-id value-field-id
                              :submit-f submit
                              :disabled? blocked?)))]
        [:div
         (when-let [trigger (or (engage-dialog-trigger (-> options :trigger)
                                                       STATE)
                                (engage-dialog-trigger [:> mui/Button {}
                                                        (-> options :title)]
                                                       STATE))]
           trigger)
         [:> mui/Dialog
          {:open (-> state :open?)
           ;; :full-width true
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
                {:type :text-1
                 :value "text-1"
                 :on-submitted (fn [value] (js/console.log value) true)})}
   "Text-1"]

  [:> mui/Button
   {:on-click #(show-dialog
                {:type :text-n
                 :value "multiline\ntext"
                 :on-submitted (fn [value] (js/console.log value) true)})}
   "Text-n"]

  [:> mui/Button
   {:on-click #(show-dialog
                {:type :code
                 :value "{:data here}"
                 :on-submitted (fn [value] (js/console.log value) true)})}
   "Code"]

  [:> mui/Button
   {:on-click #(show-dialog
                {:type :edn
                 :value {:real ["EDN" #{:value}]}
                 :on-submitted (fn [value] (js/console.log value) true)})}
   "EDN"]

  [:> mui/Button
   {:on-click #(show-dialog
                {:type :select-1
                 :value :kacper
                 :select-cols [{:key :name}
                               {:key :age}]
                 :select-options [{:id :witek
                                   :name "Witek"
                                   :age 40}
                                  {:id :kacper
                                   :name "Kacper"
                                   :age 37}]
                 :on-submitted (fn [value] (js/console.log value) true)})}
   "Select-1"]

  [:> mui/Button
   {:on-click #(show-dialog
                {:type :text-1
                 :title "Input 1"
                 :on-submitted (fn [value-a]
                                 (show-dialog
                                  {:type :text-1
                                   :title "Input 2"
                                   :on-submitted (fn [value-b]
                                                   (js/console.log value-a value-b)
                                                   true)})
                                 true)})}
   "Two Dialogs"]])
