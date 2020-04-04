(ns kunagi-base-browserapp.modules.tracking.api
  (:require
   [reagent.core :as r]
   [kunagi-base-browserapp.google-analytics :as ga]))

(defonce !track (r/atom (fn [event-name event-params]
                          (js/console.log "TRACK" event-name event-params)
                          (apply ga/track [event-name event-params]))))


(defn track!
  ([event-name]
   (track! event-name nil))
  ([event-name event-params]
   (@!track event-name event-params)))


(defn track-screen-view! [screen-name params]
  (track! "screen_view" (assoc params
                               "screen_name"
                               (if (keyword? screen-name)
                                 (name screen-name)
                                 (str screen-name)))))


(defn track-exception! [description params]
  (track! "exception" (assoc params
                             "description" description)))


(defn collect-data-from-error-event [e]
  {:colno (-> e .-colno)
   :filename (-> e .-filename)
   :lineno (-> e .-lineno)
   :type (-> e .-type)
   :message (-> e .-error .-message)
   :stack (-> e .-error .-stack)})


(defn install-error-handler! []
  (when (exists? js/window.addEventListener)
    (js/window.addEventListener
     "error"
     (fn [msg src line col error]
       (let [error-event (when (exists? (.-message msg)) msg)
             msg (if error-event
                   (.-message error-event)
                   msg)
             props (if error-event
                     (collect-data-from-error-event error-event)
                     {:msg msg :src src :line line :col col :error error})]
         (track-exception! msg props))
       false))))
