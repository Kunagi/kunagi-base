(ns kcu.bapp
  (:refer-clojure :exclude [read])
  (:require-macros [kcu.bapp])
  (:require
   [clojure.spec.alpha :as s]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [ajax.core :as ajax]

   [kcu.utils :as u]
   [kcu.registry :as registry]
   [kcu.eventbus :as eventbus]
   [kcu.projector :as projector]
   [kcu.system :as system]))

;; FIXME updates on parent lenses must be prevented
;; FIXME updates on child lenses with durable parents
;; TODO catch uncatched errors
;; TODO spec and validation for lenses
;; TODO spec for lense values


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

(def !send-message-to-server
  (atom (fn [_db _message]
          (tap> [:!!! ::send-message-to-server :not-implemented]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; init-fns


(defonce !init-fs (atom {}))


(defn reg-init-f
  [id f]
  (swap! !init-fs assoc id f))


(defn init! []
  (rf/dispatch-sync [::init]))
  ;; (eventbus/configure! {:dummy ::configuration})
  ;; (eventbus/dispatch!
  ;;  (eventbus/eventbus)
  ;;  {:event/name :bapp/initialized}
  ;;  {:dummy ::context}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn new-localstorage-store []
  (reify u/Storage
    (u/store-value [storage storage-key value]
      (-> js/window
        .-localStorage
        (.setItem (if (string? storage-key)
                    storage-key
                    (u/encode-edn storage-key))
                  (u/encode-edn value))))
    (u/load-value [storage storage-key]
      (-> js/window
          .-localStorage
          (.getItem (if (string? storage-key)
                      storage-key
                      (u/encode-edn storage-key)))))))


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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; commands & events

;; (defn dispatch
;;   [event]
;;   (eventbus/dispatch! {:dummy ::context} event))


;;; projectors


;; (defn- initial-projection-value [projector-id projection-id]
;;   (let [projector (projector/projector projector-id)
;;         durable? (-> projector :options :durable?)]
;;     (if durable?
;;       (or (load-projection projector projection-id)
;;           (projector/new-projection projector projection-id))
;;       (projector/new-projection projector projection-id))))

;; (defn projection-signal [projector-id projection-id]
;;   (if-let [signal (-> (registry/maybe-entity :projection-signal
;;                                              [projector-id projection-id])
;;                       :atom)]
;;     signal
;;     (let [signal (r/atom (initial-projection-value projector-id projection-id))]
;;       (registry/register :projection-signal
;;                          [projector-id projection-id] {:atom signal})
;;       signal)))


;; (defn projection
;;   ([projector-id]
;;    (projection :singleton))
;;   ([projector-id projection-id]
;;    @(projection-signal projector-id projection-id)))


;; (defn init-projector
;;   [projector-id options]
;;   (let [projector (projector/projector projector-id)
;;         handler-id (keyword "bapp" (name projector-id))
;;         bounded-context (-> projector :bounded-context)
;;         durable? (-> options :durable?)]

;;     ;; FIXME multiple registrations after namespace reload
;;     (eventbus/reg-handler
;;      handler-id :event-handler/catch-all {}
;;      (fn [event context]
;;        (let [p-pool (projector/new-projection-pool
;;                      projector
;;                      (fn [projector projection-id]
;;                        (or (projection (-> projector :id) projection-id)
;;                            (when durable?
;;                              (load-projection projector projection-id))))
;;                      (fn [projector projection-id value]
;;                        (reset!
;;                         (projection-signal (-> projector :id) projection-id)
;;                         value)))
;;              event-name (-> event :event/name)]
;;          (tap> [:!!! ::projector-event {:projector projector
;;                                         :event-name event-name
;;                                         :bounded-context bounded-context}])
;;          (when (= (registry/bounded-context event-name) bounded-context)
;;            (projector/handle-events projector
;;                                     p-pool
;;                                     [(assoc event :event/name
;;                                             (keyword (name event-name)))])))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; lenses


(defn lense-full-path
  "Full path to `lense` value in `app-db`."
  [lense]
  (if-let [parent (-> lense :parent)]
    (conj (lense-full-path parent) (-> lense :key))
    [(-> lense :key)]))


(defn- storage-key [lense]
  (or (-> lense :storage-key)
      (-> lense :id)))


(defn- store! [lense value]
  (tap> [:dbg ::store! {:lense (-> lense :id)
                        :value value}])
  (-> js/window
     .-localStorage
     (.setItem (storage-key lense) (u/encode-edn value))))


(defonce !loaded-lenses (atom #{}))

(defn- loaded? [lense]
  (contains? @!loaded-lenses (-> lense :id)))


(defn create-default-value! [lense]
  (when-let [default-value (-> lense :default-value)]
    (let [default-value (if (fn? default-value)
                          (default-value)
                          default-value)]
      (when (-> lense :durable?)
        (store! lense default-value))
      default-value)))


(defn- load! [lense]
  (let [value (-> js/window
                  .-localStorage
                  (.getItem (storage-key lense))
                  u/decode-edn)
        value (or value (create-default-value! lense))]
    (swap! !loaded-lenses conj (-> lense :id))
    (tap> [:dbg ::load! {:lense (-> lense :id)
                         :value value}])
    value))


(defn read
  [db lense]
  (when (and (-> lense :durable?)
             (not (loaded? lense)))
    (throw (ex-info (str "Durable lense `"
                         (-> lense :id)
                         "` not loaded. Read operation prevented.")
                    {:lense lense})))
  (get-in db (lense-full-path lense)))


(declare update-lense-subscription)

(defn swap
  [db lense f & args]
  (let [durable? (-> lense :durable?)
        path (lense-full-path lense)
        old-value (if (and durable?
                           (not (loaded? lense)))
                    (load! lense)
                    (or (get-in db path)
                        (create-default-value! lense)))
        new-value (apply f old-value args)
        new-value (if-let [setter (-> lense :setter)]
                    (setter new-value)
                    new-value)]
    (when (and durable? (not= old-value new-value))
      (store! lense new-value))
    (-> db
       (assoc-in path new-value)
       (update-lense-subscription lense))))


(defn reset
  [db lense value]
  (swap db lense (constantly value)))


(defn subscribe
  [lense]
  @(rf/subscribe [::read lense]))


(defn dispatch-reset [lense new-value]
  (rf/dispatch [::reset lense new-value]))




(defn reg-lense [lense]
  (try
    (let [id (u/getm lense :id)
          k (u/getm lense :key)
          parent (get lense :parent)
          durable? (get lense :durable?)
          auto-load? (get lense :auto-load? true)]

      (when (and durable? auto-load?)
        (reg-init-f [::load id]
                    (fn [db]
                      (-> db
                          (assoc-in (lense-full-path lense) (load! lense))
                          (update-lense-subscription lense)))))

      (when-not (and durable? auto-load?)
        (when-let [default-value-f (u/as-optional-fn (get lense :default-value))]
          (reg-init-f [::set-default-value id]
                      (fn [db]
                        (assoc-in db (lense-full-path lense) (default-value-f))))))

      ;; (when-let [projector (get lense :projector)]
      ;;   (rf/reg-event-db
      ;;    event))

      lense)
    (catch :default ex
      (throw (ex-info (str "Creating lense `" (-> lense :id) "` failed.")
                      {:lense lense
                       :cause ex}
                      ex)))))


(rf/reg-event-db
  ::reset
  (fn [db [_ lense new-value]]
    (reset db lense new-value)))


;; TODO optimize: recursive input signals
(rf/reg-sub
 ::read
 (fn [db [_ lense]]
   (read db lense)))



;;; default lenses


(def bapp
  (reg-lense {:id ::bapp
              :key :bapp}))



;;; errors

(def errors
  (reg-lense {:id ::errors
              :key :errors
              :parent bapp}))


;;; anti-forgery


(defonce !anti-forgery-token (atom nil))

(defn POST
  [endpoint options]
  ;; FIXME re-request token if POST response contains "invalid anti-forgery token"
  (if-let [anti-forgery-token @!anti-forgery-token]
    (ajax/POST
     endpoint
     (-> options
         (assoc-in [:headers "X-CSRF-Token"] anti-forgery-token)))
    (ajax/GET
     "/api/anti-forgery-token"
     {:handler (fn [token]
                 (reset! !anti-forgery-token token)
                 (POST endpoint options))})))



;;; conversation


(s/def ::conversation-id (s/and string?
                                #(> (count %) 8)))


(def conversation
  (reg-lense {:id ::conversation
              :key :conversation
              :parent bapp}))


(def conversation-id
  (reg-lense {:id ::conversation-id
              :key :id
              :parent conversation}))



(def server-subscriptions
  (reg-lense {:id ::server-subscriptions
              :key :server-subscriptions
              :parent conversation
              :default-value {}}))


(defn server-subscription-by-query
  [db query]
  (-> db
      (read server-subscriptions)
      vals
      (->> (filter (fn [subscription] (= query (-> subscription :query)))))
      first))





;; (reg-init-f
;;  ::continue-conversation
;;  (fn [db]
;;    (transmit-messages-to-server! db [])
;;    db))


;; (defn fetch-messages-from-server
;;   [])


(defn- run-init-fs [db]
  (reduce (fn [db [id init-f]]
            (try
              (init-f db)
              (catch :default ex
                (tap> [:err ::init-f-failed {:id id :init-f init-f :ex ex}])
                db)))
          db @!init-fs))


(defn transmit-messages-to-server!
  [db messages]
  (let [conversation-id (read db conversation-id)]
    (u/assert-spec ::conversation-id conversation-id ::transmit-messages-to-server!)
    (@!send-message-to-server db [::conversation-messages
                                  {:conversation-id conversation-id
                                   :messages messages}])
    (POST
     "/api/post-messages"
     {
      :format :text
      :params {:conversation conversation-id
               :messages messages}
      ;; :body (u/encode-edn {:conversation (read db conversation-id)
      ;;                      :messages messages})
      :handler (fn [response]
                 (tap> [:dbg ::messages-delivered-to-server
                        {:response response
                         :messages messages}]))})
    db))


(defn transmit-message-to-server!
  [db message]
  (transmit-messages-to-server! db [message]))


(defn poll-messages-from-server!
  [db]
  (let [conversation-id (read db conversation-id)]
    (u/assert-spec ::conversation-id conversation-id ::poll-messages-from-server!)
    (ajax/GET
     "/api/messages"
     {:params {:conversation conversation-id
               :wait true}
      :handler (fn [response]
                 (rf/dispatch [::handle-poll-messages-from-server-response response]))
      :error-handler (fn [response]
                       (tap> [:wrn ::poll-messages-from-server!-failed response])
                       (u/invoke-later! 1000 #(rf/dispatch [::restart-conversation])))})
    db))


(defn- handle-query-response
  [db {:keys [query response]}]
  (if-let [lense (:lense (server-subscription-by-query db query))]
    (reset db lense response)
    (do
      (tap> [:wrn ::no-lense-found-for-query query])
      db)))

(defn- handle-message-from-server
  [db message]
  (tap> [:dbg ::message-from-server message])
  (case (-> message :type)
    :query-response (handle-query-response db message)
    (do
      (tap> [:wrn ::handle-message-from-server :unsupported-message message])
      db)))

(rf/reg-event-db
 ::handle-poll-messages-from-server-response
 (fn [db [_ response]]
   (let [messages (u/decode-edn response)
         db (reduce handle-message-from-server db messages)]
     (poll-messages-from-server! db)
     db)))


(defn transmit-subscriptions-to-server!
  [db]
  (let [queries (-> db
                    (read server-subscriptions)
                    vals
                    (->> (remove (fn [sub] (not (-> sub :query))))
                         (map :query))
                    (into #{}))]
    (transmit-message-to-server!
     db
     {:type :subscriptions
      :subscriptions queries})))


(defn update-server-subscriptions
  [db new-subscriptions]
  ;; FIXME optimize: check if changed
  (let [subscriptions (read db server-subscriptions)
        subscriptions (merge subscriptions new-subscriptions)
        db (reset db server-subscriptions subscriptions)]
    (transmit-subscriptions-to-server! db)
    db))

(rf/reg-event-db
 ::update-server-subscriptions
 (fn [db [_ new-subscriptions]]
   (update-server-subscriptions db new-subscriptions)))

(defn update-lense-subscription
  [db lense]
  (if-let [query-f (u/as-optional-fn (get lense :server-subscription-query))]
    (update-server-subscriptions
     db
     {[:lense (-> lense :id)]
      {:query (query-f db lense)
       :lense lense}})
    db))


(defn start-conversation
  [db]
  (let [db (reset db conversation-id (u/random-uuid-string))]
    (poll-messages-from-server! db)
    (transmit-subscriptions-to-server! db)
    db))


(rf/reg-event-db
 ::restart-conversation
 (fn [db _]
   (start-conversation db)))


(rf/reg-event-db
 ::init
 (fn [db]
   (-> db
       start-conversation
       run-init-fs)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
