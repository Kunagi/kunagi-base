(ns kcu.modx
  (:require
   [kcu.app :as app]))


(app/def-event app-stopped
  {:a :b})


(app/def-event-handler on-app-stopped
  {:event app-stopped
   :f (fn [context event]
        context)})


(-> app/!appmodel deref :idx :event)
