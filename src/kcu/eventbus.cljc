(ns kcu.eventbus
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]
   [kcu.registry :as registry]))


(s/def ::handler-id qualified-keyword?)
(s/def ::handler-f fn?)
(s/def ::handler-options map?)

(s/def ::event-name qualified-keyword?)


(defn new-eventbus []
  {:handlers {}
   :options {}})


(defn handlers-for-event
  [eventbus event-name]
  (concat
   (->> eventbus :handlers-by-event :event-handler/catch-all)
   (->> eventbus
        :handlers-by-event
        (get event-name))))


(defn add-handler
  [eventbus handler]
  (u/assert-entity handler {:id ::handler-id
                            :event ::event-name
                            :f ::handler-f
                            :options ::handler-options})
  (-> eventbus
      (assoc-in [:handlers (-> handler :id)] handler)
      (update-in [:handlers-by-event (-> handler :event)] conj handler)))


(defn- handler->f [_eventbus event handler]
  (fn [context]
    (try
      ((-> handler :f) context event)
      (catch #?(:clj Exception :cljs :default) ex
        (throw (ex-info (str "Event handler `"
                             (-> handler :id)
                             "` crashed on event `"
                             (-> event :event/name)
                             "`.")
                        {:event event
                         :handler handler}
                        ex))))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn reg-handler
  [handler-id event-name options f]
  (registry/update-entity
   :eventbus :singleton
   (fn [eventbus]
     (let [eventbus (or eventbus (new-eventbus))]
       (add-handler eventbus {:id handler-id
                              :event event-name
                              :f f
                              :options options}))))
  handler-id)


(defn configure! [options]
  (registry/update-entity :eventbus :singleton
                          (fn [eventbus]
                            (assoc eventbus :options options))))


(defn eventbus []
  (registry/entity :eventbus :singleton))


(defn- handle-event [handler event context]
  (try
    ((-> handler :f) event context)
    (catch #?(:clj Exception :cljs :default) ex
      (throw (ex-info (str "Handling event `" (-> event :event/name) "` failed."
                           " Event handler `" (-> handler :id) "` crashed. ")
                      {:event event
                       :hander handler
                       :exception ex}
                      ex))))
  nil)


(defn dispatch!
  [context event]
  (u/assert-entity event {:event/name ::event-name})
  (let [event-name (-> event :event/name)
        _ (tap> [:inf ::event (-> event :event/name) event])
        eventbus (eventbus)
        log-f (-> eventbus :log-f)
        _ (when log-f (log-f event))
        handlers (handlers-for-event eventbus event-name)]
    (doseq [handler handlers]
      (handle-event handler event context)))
  nil)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; rich comments

