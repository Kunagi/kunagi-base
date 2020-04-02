(ns kcu.eventbus
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]))


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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- handle-event [handler event context]
  (try
    ((-> handler :f) event)
    (catch #?(:clj Exception :cljs :default) ex
      (throw (ex-info (str "Handling event `" (-> event :event/name) "` failed."
                           " Event handler `" (-> handler :id) "` crashed. ")
                      {:event event
                       :hander handler
                       :exception ex}
                      ex))))
  nil)


(defn dispatch!
  [eventbus event context]
  (u/assert-entity event {:event/name ::event-name})
  (let [event-name (-> event :event/name)
        handlers (handlers-for-event eventbus event-name)]
    (tap> [:inf ::event (-> event :event/name) {:event event
                                                :handlers (map :id handlers)}])
    (doseq [handler handlers]
      (handle-event handler event context)))
  nil)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; rich comments

