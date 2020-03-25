(ns kcu.events
  (:require
   [kcu.app-model :as app-model]
   [kcu.app :as app]))


(defn validate-context [context])
;; TODO


(defn apply-handler [context handler event]
  (let [handle (-> handler :f)
        context (handle context event)]
    (validate-context context)
    context))


(defn handle-event [context event]
  (let [[event-id] event
        model @app/!app-model
        handlers (models-by-index model :app/event-handler :event event-id)
        _ (tap> [:!!! ::handle-event {:event-handlers handlers}])]
    (reduce (fn [context handler]
              (apply-handler context handler event))
            context
            handlers)))
