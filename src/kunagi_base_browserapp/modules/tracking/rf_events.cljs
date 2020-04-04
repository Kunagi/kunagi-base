(ns kunagi-base-browserapp.modules.tracking.rf-events
  (:require
   [re-frame.core :refer [reg-event-db]]
   [kunagi-base-browserapp.modules.tracking.api :as tracking]))


;; TODO use fx

(reg-event-db
 :tracking/screen-view
 (fn [db [_ screen-name params]]
   (tracking/track-screen-view! screen-name params)
   db))


(reg-event-db
 :tracking/event
 (fn [db [_ event-name params]]
   (tracking/track! event-name params)
   db))
