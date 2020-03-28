(ns kcu.eventbus
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]))


(s/def ::handler-id qualified-keyword?)
(s/def ::handler-f fn?)

(s/def ::event-name qualified-keyword?)

(s/def ::eventbus-identifier (partial = ::eventbus))
(s/def ::eventbus (s/keys :req [::eventbus-identifier]))


(defn new-eventbus [options]
  {::eventbus-identifier ::eventbus
   :handlers {}
   :options options})


(defn handlers-for-event
  [eventbus event-name]
  (u/assert-spec ::eventbus eventbus)
  (->> eventbus
       :handlers
       vals
       (filter #(= event-name (-> % :event)))))


(defn reg-handler
  [eventbus handler]
  (u/assert-spec ::eventbus eventbus)
  (u/assert-entity handler {:id ::handler-id
                            :event ::event-name
                            :f ::handler-f})
  (-> eventbus
      (assoc-in [:handlers (-> handler :id)] handler)))


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


(defn handle-event
  [eventbus context event]
  (u/assert-spec ::eventbus eventbus)
  (u/assert-entity event {:event/name ::event-name})
  (let [event-name (-> event :event/name)
        handlers (handlers-for-event eventbus event-name)
        handle-fs (map (partial eventbus event) handlers)]
    (reduce (fn [context f]
              (f context))
            context handle-fs)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; effects

(defonce !event-bus (atom {}))


;; (defn reg-handler
;;   [handler]
;;   (let [handler-id (complete-handler handler)]
;;     (swap! !bu assoc handler-id handler)
;;     handler-id))


;; (defn dispatch!
;;   [event]
;;   (u/assert-entity event {:event/name ::event-name})
;;   (let [event-name (-> event :event/name)
;;         handlers (handlers-for-event event-name)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; rich comments

