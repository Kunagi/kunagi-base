(ns kunagi.redakti.ui
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]
   [mui-commons.components :as muic]

   [kunagi.redakti :as redakti]
   [kunagi.redakti.buffer-ui :as buffer-ui]))


(defonce !redakti (r/atom nil))


(defn init! [redakti]
  (reset! !redakti redakti))


(defn FeedbackBar [redakti]
  [:div.FeedbackBar
   (-> redakti :buffer :cursor str)
   " > "
   (when-let [message (-> redakti :message)]
     (let [[type text] message]
       [:span
        {:style {:color (case type
                          :err :red
                          :grey)}}
        text]))])


(defn Redakti [redakti]
  [muic/Stack
   [FeedbackBar redakti]
   [buffer-ui/Buffer (-> redakti :buffer)]])
   ;; [:div.DEBUG
   ;;  [muic/Data (-> @!redakti :buffer :cursor)]
   ;;  [muic/Data redakti]]])


(defn Redakti! []
  [muic/Focusable
   {:on-key-press #(do
                     ;;(js/console.log (-> % .-key) "|" (-> %)))}
                     (swap! !redakti redakti/!action-for-key (-> % .-key))
                     (-> % .preventDefault))}
    ;; :style {:background-color :black
    ;;         :color :white}}
   [Redakti
    @!redakti]])
