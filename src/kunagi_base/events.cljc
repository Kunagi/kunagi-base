(ns kunagi-base.events
  (:require
   [kunagi-base.auth.api :as auth]
   [kunagi-base.context :as context]
   [kunagi-base.appmodel :as appmodel :refer [def-extension]]))


(def-extension
  {:schema {:event-handler/module {:db/type :db.type/ref}
            :event/module {:db/type :db.type/ref}
            :event/ident {:db/unique :db.unique/identity}}})


(defn def-event-handler [event-handler]
  (appmodel/register-entity
   :event-handler
   event-handler))


(defn def-event [event]
  (appmodel/register-entity
   :event
   event))


(defn- handler-handle-event [context event [event-handler-id]]
  (tap> [:dbg ::handle-event {:event event
                              :handler event-handler-id}])
  (let [event-handler (appmodel/entity! event-handler-id)
        f (-> event-handler :event-handler/f)]
    (f event context)))


(defn event-dispatch-permitted? [event-v context]
  (if (auth/context-authorized? context)
    true
    (let [event-ident (first event-v)
          event (appmodel/entity! [:event/ident event-ident])
          req-perms (-> event :event/req-perms)]
      ;; (tap> [:!!! ::event event-v event req-perms])
      (if (nil? req-perms)
        false
        (auth/context-has-permissions? context req-perms)))))


(defn dispatch-event! [event context]
  (tap> [:dbg ::event event])
  (let [event-ident (first event)]
    (if-not (event-dispatch-permitted? event context)
      (let [response-f (-> context :comm/response-f)]
        (tap> [:inf ::event-dispatch-denied {:event event
                                             :user (-> context :auth/user-id)}])
        (response-f [:auth/server-event-not-permitted event]))
      (let [event-handlers (appmodel/q!
                            '[:find ?e
                              :in $ ?event-ident
                              :where
                              [?e :event-handler/f _]
                              [?e :event-handler/event-ident ?event-ident]]
                            [event-ident])]
        (doseq [event-handler event-handlers]
          (handler-handle-event context event event-handler))))))
