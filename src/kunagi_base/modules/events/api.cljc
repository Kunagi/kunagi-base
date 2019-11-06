(ns kunagi-base.modules.events.api
  (:require
   [kunagi-base.utils :as utils]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.context :as context]
   [kunagi-base.appmodel :as am]))


(defn- handler-handle-event [context event [event-handler-id]]
  (tap> [:dbg ::handle-event {:event event
                              :handler event-handler-id}])
  (let [event-handler (am/entity! event-handler-id)
        f (-> event-handler :event-handler/f)]
    (f event context)))


(defn- event-dispatch-permitted? [event-v context]
  (if (auth/context-authorized? context)
    true
    (let [event-ident (first event-v)
          event (am/entity! [:event/ident event-ident])
          req-perms (-> event :event/req-perms)]
      ;; (tap> [:!!! ::event-dispatch-permitted? {:event-v event-v
      ;;                                          :event event
      ;;                                          :req-perms req-perms
      ;;                                          :user-perms (-> context :auth/user-perms)
      ;;                                          :context context}])
      (if (nil? req-perms)
        false
        (auth/context-has-permissions? context req-perms)))))


(defn dispatch-event! [context event]
  (tap> [:dbg ::event event])
  (let [event-ident (first event)]
    (if-not (event-dispatch-permitted? event context)
      (do
        (tap> [:wrn ::event-dispatch-denied
               {:event event
                :req-perms (-> (am/entity! [:event/ident event-ident]) :event/req-perms)
                :user (-> context :auth/user-id)
                :user-perms (-> context :auth/user-perms)}])
        (if-let [response-f (-> context :comm/response-f)]
          (response-f [:auth/server-event-not-permitted event])))
      (let [event-handlers (am/q!
                            '[:find ?e
                              :in $ ?event-ident
                              :where
                              [?e :event-handler/event-ident ?event-ident]]
                            [event-ident])]
        (when (empty? event-handlers)
          (tap> [:wrn ::no-event-handlers {:event-ident event-ident
                                           :known-handlers (am/q!
                                                            '[:find ?e ?ident
                                                              :where
                                                              [?e :event-handler/event-ident ?ident]])}]))
        (doseq [event-handler event-handlers]
          (handler-handle-event context event event-handler))))))
