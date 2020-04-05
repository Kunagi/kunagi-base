(ns kcu.bapp
  (:refer-clojure :exclude [read])
  (:require-macros [kcu.bapp])
  (:require
   [clojure.spec.alpha :as s]
   [reagent.dom :as rdom]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [taoensso.sente  :as sente]

   [kcu.tap]

   [kcu.utils :as u]
   [kcu.butils :as bu]
   [kcu.config :as config]
   [kcu.registry :as registry]
   [kcu.eventbus :as eventbus]
   [kcu.projector :as projector]
   [kcu.system :as system]))


;; TODO catch uncatched errors


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn durable-uuid
  [identifier]
  (let [storage-key (str "uuid." identifier)
        uuid (-> js/window .-localStorage (.getItem storage-key))]
    (if uuid
      uuid
      (let [uuid (u/random-uuid-string)]
        (-> js/window .-localStorage (.setItem storage-key uuid))
        uuid))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce SENTE_SOCKET (r/atom nil))

(defonce SENTE_STATE (r/atom nil))


(defn- on-sente-state-changed [_ _ _ state]
  (tap> [:dbg ::on-sente-state-changed state])
  (reset! SENTE_STATE state))


(defmulti on-server-message (fn [message-id _payload] message-id))

(defmethod on-server-message :chsk/ws-ping [_ _])


(defn- on-sente-message-received [message]
  (when (= :chsk/recv (-> message :id))
    (let [[message-id payload] (-> message :event second)]
      (tap> [:dbg ::on-sente-message-received message-id payload])
      (on-server-message message-id payload))))


(defn connect-to-server []
  (tap> [:dbg ::connect-to-server])
  (let [socket (sente/make-channel-socket-client! "/chsk" {:type :auto})
        ch-recv (-> socket :ch-recv)
        state (-> socket :state)]
    (reset! SENTE_SOCKET socket)
    (add-watch state ::status on-sente-state-changed)
    (sente/start-client-chsk-router! ch-recv on-sente-message-received)
    socket))


(defn send-message-to-server [message]
  (tap> [:dbg ::send-message-to-server message])
  (if (get @SENTE_STATE :open?)
    ((get @SENTE_SOCKET :send-fn) message)
    (.setTimeout js/window
                 #(send-message-to-server message)
                 1000)))


(defonce COMMAND-CALLBACKS (atom {}))


(defn dispatch-on-server
  ([command]
   (dispatch-on-server command nil))
  ([command callback]
   (let [command (assoc command :command/id
                        (or (-> command :command/id)
                            (u/random-uuid-string)))]
     (when callback
       (swap! COMMAND-CALLBACKS assoc (-> command :command/id) callback))
     (send-message-to-server [::dispatch command]))))


(defmethod on-server-message :sapp/command-callback
  [_ {:keys [command-id result]}]
  ;; TODO default handlers for result and failure when there is no callback
  (when command-id
    (when-let [callback (get @COMMAND-CALLBACKS command-id)]
      (callback result)
      (swap! COMMAND-CALLBACKS dissoc command-id))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn projection-value-key [projector-id projection-id]
  (str "projections/"
       (name projector-id)
       (when projection-id
         (str "/" (cond
                    (simple-keyword? projection-id) (name projection-id)
                    (qualified-keyword? projection-id)
                    (str (namespace projection-id) "_" (name projection-id))
                    :else projection-id)))
       ".edn"))


(defn projection-storage []
  (reify system/ProjectionStorage

    (system/store-projection-value [_this projector-id projection-id value]
      (bu/set-to-local-storage
       (projection-value-key projector-id projection-id) value))

    (system/load-projection-value [_this projector-id projection-id]
      (bu/get-from-local-storage
       (projection-value-key projector-id projection-id)))))


(defonce system (system/new-system
                 :bapp
                 {:projection-storage (projection-storage)}))


;; TODO rename to something with "query"
(defn projection-bucket [projector-id projection-id]
  (system/projection-bucket system projector-id projection-id))


(defn projection [projector-id projection-id]
  (system/projection system projector-id projection-id))


(defn dispatch [command]
  (system/dispatch-command system command))


;; FIXME resend subscription requenst when server restarted

(defonce SUBSCRIPTIONS_ON_SERVER (atom #{}))


(defn subscribe-on-server [query]
  (when-not (contains? @SUBSCRIPTIONS_ON_SERVER query)
    (tap> [:dbg ::subscribe-on-server query])
    (swap! SUBSCRIPTIONS_ON_SERVER conj query)
    (send-message-to-server [::subscribe query])))


(defmethod on-server-message :sapp/subscription-changed
  [_ {:keys [subscription new-value]}]
  (let [projector-id (-> subscription :projection/projector)
        projection-id (-> subscription :projection/id)]
    (system/merge-projection system
                             projector-id projection-id
                             new-value)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; ui components


(defn reg-component
  [id component-f model-type options]
  (registry/register
   :ui-component
   id
   (merge
    {:id id
     :f component-f
     :model-type model-type}
    options))
  id)


(defn components []
  (registry/entities :ui-component))


(defn components-by-model-type [model-type]
  (registry/entities-by :ui-component :model-type model-type))



;;; startup ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- install-serviceworker []
  (when (-> (config/appinfo) :browserapp :serviceworker)
    ;; serviceworker is supported for this app
    (when (get (config/config) :service-worker? true)
      ;; serviceworder is not disabled for this installation
      (if-not (-> js/navigator .-serviceWorker)
        (tap> [:wrn ::serviceworker-not-supported-by-browser])
        (try
          (js/navigator.serviceWorker.register "/serviceworker.js")
          (catch :default ex
            (tap> [:err ::serviceworker-installation-failed ex])))))))


(defn start []
  (install-serviceworker)
  (rf/dispatch-sync [::init])
  (connect-to-server))
;; (eventbus/configure! {:dummy ::configuration})
;; (eventbus/dispatch!
;;  (eventbus/eventbus)
;;  {:event/name :bapp/initialized}
;;  {:dummy ::context}))


(defn mount-app
  [root-component-f]
  (rdom/render [root-component-f] (.getElementById js/document "app")))
