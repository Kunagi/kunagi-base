(ns kunagi-base.events
  (:require
   [kunagi-base.context :as context]
   [kunagi-base.appmodel :as appmodel :refer [def-extension]]))


(def-extension
  {:schema {:event-handler/module {:db/type :db.type/ref}}})


(defn def-event-handler [event-handler]
  (appmodel/register-entity
   :event-handler
   event-handler))


(defn- handler-handle-event [context event [event-handler-id]]
  (tap> [:dbg ::handle-event {:event event
                              :handler event-handler-id}])
  (let [event-handler (appmodel/entity! event-handler-id)
        f (-> event-handler :event-handler/f)]
    (f event context)))


(defn dispatch-event! [event context]
  (tap> [:dbg ::event event])
  ;; FIXME check permissions
  (let [event-ident (first event)
        event-handlers (appmodel/q!
                        '[:find ?e
                          :in $ ?event-ident
                          :where
                          [?e :event-handler/f _]
                          [?e :event-handler/event-ident ?event-ident]]
                        [event-ident])]
    (doseq [event-handler event-handlers]
      (handler-handle-event context event event-handler))))

