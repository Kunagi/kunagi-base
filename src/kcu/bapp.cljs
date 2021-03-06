(ns kcu.bapp
  (:refer-clojure :exclude [read])
  (:require-macros [kcu.bapp])
  (:require
   [clojure.spec.alpha :as s]
   [reagent.dom :as rdom]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [re-frame.db :as rf-db]
   [ajax.core :as ajax]
   [taoensso.sente  :as sente]
   [accountant.core :as accountant]

   [kcu.tap]

   [kcu.utils :as u]
   [kcu.butils :as bu]
   [kcu.config :as config]
   [kcu.registry :as registry]
   [kcu.eventbus :as eventbus]
   [kcu.projector :as projector]
   [kcu.system :as system]))


;; TODO catch uncatched errors


;;; navigation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn navigate! [page-ident page-args]
  (accountant/navigate!
   (str "/ui/"
        (bu/url-encode-path+args
         (if (= :index page-ident)
           ""
           (name page-ident))
         page-args))))


;;; re-frame ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(rf/reg-sub :bapp/db (fn [db [_ & path]] (get-in db path)))


(defn subscribe [sub]
  (when-let [signal (rf/subscribe sub)]
    @signal))


(defn update-db [f]
  (swap! rf-db/app-db f))


(defn update-in-db [path f]
  (swap! rf-db/app-db update-in path f))


(defn assoc-in-db [path value]
  (swap! rf-db/app-db assoc-in path value))


(defn reg-sub-f [model-k sub-k f]
  (rf/reg-sub
   sub-k
   (fn [db sub]
     (if-let [model (get db model-k)]
       (apply f (into [model] (rest sub)))))))


(defn reg-event-f [model-k event-k f]
  (rf/reg-event-db
   event-k
   (fn [db event]
     (let [model (get db model-k)
           model (apply f (into [model] (rest event)))]
       (assoc db model-k model)))))


;;; ajax ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn request-edn [url db-k f]
  (bu/GET-edn url
              (fn [value error]
                (when value
                  (update-in-db [db-k] #(f % value))))))


(defn request-edn-for-db [url path-in-db]
  (bu/GET-edn url
              (fn [value error]
                (when value
                  (assoc-in-db path-in-db value)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn durable-uuid
  [identifier]
  (let [storage-key (str "uuid." identifier)
        uuid (bu/get-from-local-storage storage-key)]
    (if uuid
      uuid
      (let [uuid (u/random-uuid-string)]
        (bu/set-to-local-storage storage-key uuid)
        uuid))))


(defn dev-mode? []
  (-> (config/config) :dev-mode?))


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



;;; assets ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn new-asset [id options]
  (let [durable? (-> options :durable?)]
     (if durable?
       (bu/durable-ratom [:bapp/asset id])
       (r/atom nil))))


;;;; projections ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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
  (if (vector? command)
    (rf/dispatch command)
    (system/dispatch-command system command)))


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


;;; auth ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce USER-ID (bu/durable-ratom :bapp/user-id))
(defonce USER (bu/durable-ratom :bapp/user))

(defn user-id [] @USER-ID)
(defn user [] @USER)

(def authenticated? user-id)


(defn- user-has-permission? [req-perm]
  (when-let [user (user)]
    (let [user-perms (or (-> user :user/perms)
                         #{})]
      (contains? user-perms req-perm))))


(defn- update-user [new-value]
  (swap! USER (fn [user]
                (if (or (not= (-> user :user/id) (-> new-value :user/id))
                        (> (count (keys new-value)) 1))
                  new-value
                  (merge user new-value)))))


(defmethod on-server-message :sapp/user-authenticated
  [_ user-id]
  ;; (when-let [prev-user-id @USER-ID]
  ;;   (when (not= user-id prev-user-id)
  ;;     (bu/clear-all-data-and-reload)))
  (reset! USER-ID user-id)
  (update-user {:user/id user-id}))


(defn clear-all-data-and-sign-out []
  (bu/clear-all-data)
  (bu/navigate-to "/sign-out"))


(u/do-once

 ;; to prevent mixing up user data reload the page if user changes or signs out

 (bu/subscribe-to-local-storage-clear
  (fn []
    (bu/reload-page)))

 (bu/subscribe-to-local-storage
  :bapp/user-id (fn [new-user-id]
                  (when-not (= new-user-id (user-id))
                    (bu/reload-page)))))


;;; startup ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def set-appinfo config/set-appinfo)


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
  (connect-to-server))
;; (eventbus/configure! {:dummy ::configuration})
;; (eventbus/dispatch!
;;  (eventbus/eventbus)
;;  {:event/name :bapp/initialized}
;;  {:dummy ::context}))


(defn mount-app
  [root-component-f]
  (rdom/render [root-component-f] (.getElementById js/document "app")))
