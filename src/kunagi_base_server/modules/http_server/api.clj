(ns kunagi-base-server.modules.http-server.api
  (:require
   [clojure.spec.alpha :as s]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.params :as ring-params]
   [ring.middleware.file :as ring-file]
   [ring.middleware.resource :as ring-resource]
   [ring.middleware.content-type :as ring-content-type]
   [ring.middleware.not-modified :as ring-not-modified]
   [ring.middleware.reload :as ring-reload]
   [ring.util.response :as ring-resp]
   [ring.util.mime-type :as ring-mime]
   [org.httpkit.server :as http-kit]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.http-kit :as sente-adapter]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]

   [kunagi-base.modules.events.api :as events]

   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am]
   [kunagi-base.context :as context]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.cqrs.api :as cqrs]
   [kunagi-base-server.modules.assets.api :as assets]
   [kcu.sapp :as sapp]))


(s/def ::route-path string?)


;;; appmodel


;;;


(defn- request-permitted?
  [context {:as handler
            :keys [req-perms]}]
  (if (nil? req-perms)
    false
    (auth/context-has-permissions? context req-perms)))


(defn- new-get-handler
  [{:as handler
    :keys [serve-f]}]
  (fn [req]
    (let [context (-> (context/from-http-request req)
                      (auth/update-context))]
      (if-not (request-permitted? context handler)
        {:status 403
         :body "Forbidden"}
        (try
          (serve-f context)
          (catch Throwable ex
            (tap> [:err ::http-request-handlig-failed ex])
            {:status 500
             :body "Internal Server Error"}))))))


(defn- new-post-handler
  [{:as handler
    :keys [serve-f]}]
  (fn [req]
    (let [context (-> (context/from-http-request req)
                      (auth/update-context))]
      (if-not (request-permitted? context handler)
        {:status 403
         :body "Forbidden"}
        (try
          (serve-f context)
          "ok"
          (catch Throwable ex
            (tap> [:err ::http-request-handlig-failed ex])
            {:status 500
             :body "Internal Server Error"}))))))


(defn GET
  [{:as handler
    :keys [path]}]
  (s/assert ::route-path path)
  (compojure/GET path [] (new-get-handler handler)))


(defn POST
  [{:as handler
    :keys [path]}]
  (s/assert ::route-path path)
  (compojure/POST path [] (new-post-handler handler)))


(defn- serve-file [file context]
  (if (and file (.exists file))
    (let [filename (or (-> context :http/request :params :filename)
                       (.getName file))]
      (-> (ring-resp/file-response (.getPath file) {})
          (ring-resp/content-type (ring-mime/ext-mime-type filename))
          (ring-resp/header "Content-Disposition"
                            (str "attachment; filename=\""
                                 filename
                                 "\""))))
    (ring-resp/not-found)))


;;; cqrs routes


(defn- a-query-handler [context query-result>http-response]
  (if-let [edn (-> context :http/request :params :edn)]
    (let [query (read-string edn)]
      (try
        (let [result (cqrs/query-sync context query)]
          (query-result>http-response result))
        (catch Throwable ex
          (tap> [:dbg ::querying-failed ex])
          {:status 400
           :body "Querying failed"})))
    {:status 400
     :body "Missing Parameter: [edn]"}))


(defn- serve-query-data [context]
  (a-query-handler context
                   (fn [result]
                     (-> result
                         (dissoc :context)
                         pr-str
                         (ring-resp/response)
                         (ring-resp/content-type "text/edn")))))


(defn- serve-query-file [context]
  (a-query-handler context
                   (fn [result]
                     (let [results (get result :results)
                           file (first results)]
                       (serve-file file context)))))

;;; appmodel routes


;;; asset route


(defn- serve-asset [context]
  (if-let [edn (-> context :http/request :params :edn)]
    (let [[asset-pool-ident asset-path] (read-string edn)]
      (try
        (let [asset (assets/asset-for-output asset-pool-ident asset-path context)]
          (if (instance? java.io.File asset)
            (serve-file asset context)
            (-> asset
                pr-str
                (ring-resp/response)
                (ring-resp/content-type "text/edn"))))
        (catch Throwable ex
          (tap> [:dbg ::providing-asset-failed ex])
          {:status 400
           :body "Providing asset failed"})))
    {:status 400
     :body "Missing Parameter: [edn]"}))


(defn- routes-from-appmodel []
  (let [routes (am/q!
                '[:find ?path ?serve-f ?req-perms ?method
                  :where
                  [?r :route/path ?path]
                  [?r :route/serve-f ?serve-f]
                  [?r :route/req-perms ?req-perms]
                  [?r :route/method ?method]])]
    (map
     (fn [[path serve-f req-perms method]]
       (if (= :post method)
         (POST {:path path :serve-f serve-f :req-perms req-perms})
         (GET {:path path :serve-f serve-f :req-perms req-perms})))
     routes)))


(defn- wrappers-from-appmodel [app-db]
  (let [wrappers (am/q!
                  '[:find ?wrapper-f
                    :where
                    [?r :routes-wrapper/wrapper-f ?wrapper-f]])]
    (map
     (fn [[wrapper-f]]
       (wrapper-f app-db))
     wrappers)))


;;; sente

(defn determine-sente-user-id [request]
  (str
   (-> request :session :auth/user-id)
   "/"
   (-> request :client-id)))


(defn- on-connections-changed
  [old-val new-val]
  (let [old-ids (:any old-val)
        new-ids (:any new-val)
        connected-ids (remove old-ids new-ids)
        disconnected-ids (remove new-ids old-ids)]
    (doseq [client-id connected-ids]
      (tap> [:dbg ::connected client-id]))
    (doseq [client-id disconnected-ids]
      (tap> [:dbg ::disconnected client-id]))))


(defn- on-event-received [event context]
  (events/dispatch-event! context event))


(defn- respond-to-client [send-fn sente-user-id event]
  (send-fn sente-user-id [:kunagi-base/event event]))


(defn- respond-f [data]
  (fn [message]
    (let [send-fn (-> data :send-fn)
          uid (-> data :uid)]
      (send-fn uid message))))


(defn- context-from-http-async-data [data]
  (-> data
      context/from-http-async-data
      auth/update-context
      (assoc :comm/response-f (respond-f data))))


(defn- on-subscription-changed
  [subscription respond-f new-value unsubscribe-f]
  (respond-f [:sapp/subscription-changed {:subscription subscription
                                          :new-value new-value}]))


(defn- on-command-callback
  [command respond-f result]
  (respond-f [:sapp/command-callback {:command-id (-> command :command/id)
                                      :result result}]))


(defn- on-data-received [data]
  ;; (tap> [:!!! ::data-received data])

  (case (-> data :id)

    :chsk/ws-ping
    ::nop

    :chsk/uidport-open
    ::nop

    :chsk/uidport-close
    ::nop

    :kcu.bapp/dispatch
    (sapp/dispatch-from-bapp (-> data :event second)
                             (partial on-command-callback
                                      (-> data :event second)
                                      (respond-f data))
                             (context-from-http-async-data data))

    :kcu.bapp/subscribe
    (sapp/subscribe (-> data :event second)
                    (partial on-subscription-changed
                             (-> data :event second)
                             (respond-f data))
                    (context-from-http-async-data data))

    :kcu.bapp/conversation-messages
    (tap> [:!!! ::conversation-messages-received (-> data :event second)])

    :kunagi-base/event
    (on-event-received (-> data :event second)
                       (context-from-http-async-data data))

    (tap> [:err ::unsupported-async-message-received data])))



(defn- create-socket
  []
  (let [socket (sente/make-channel-socket!
                (sente-adapter/get-sch-adapter)
                {:user-id-fn determine-sente-user-id})]
    (add-watch (:connected-uids socket)
               :connected-uids
               (fn [_ _ old-val new-val]
                 (when (not= old-val new-val)
                   (on-connections-changed old-val new-val))))
    (sente/start-server-chsk-router!
     (:ch-recv socket)
     #(on-data-received %))
     ;#(when-not (= "chsk" (-> % :id)) on-data-received %))
    socket))


(defonce !sente-socket (atom nil))

(defn- sente-socket []
  (if-let [socket @!sente-socket]
    socket
    (let [socket (create-socket)]
      (reset! !sente-socket socket)
      socket)))


(defn- serve-sente-post [req]
  (let [socket (sente-socket)
        post-f (:ajax-post-fn socket)]
    (if post-f
      (post-f req)
      (do
        (tap> [:err ::missing-sente-ajax-post-fn {:socket socket}])
        nil))))


;;; http server

(defn- create-default-routes
  []
  [(GET {:path "/api/asset"
         :serve-f serve-asset
         :req-perms [:assets/read]})
   (GET {:path "/api/query"
         :serve-f serve-query-data
         :req-perms [:cqrs/query]})
   (GET {:path "/api/query-file"
         :serve-f serve-query-file
         :req-perms [:cqrs/query]})

   (compojure/GET  "/chsk" [] (fn [req] ((:ajax-get-or-ws-handshake-fn (sente-socket)) req)))
   (compojure/POST "/chsk" [] serve-sente-post)

   ;; (compojure-route/files "/"        {:root "public-web-resources"})
   ;; (compojure-route/resources "/"    {:root "public"})
   (compojure-route/not-found        "404 - Page not found")])



;; (defn- routes-from-cqrs [context]
;;   (->> (cqrs/query-sync context [:http-server/routes])
;;        :results
;;        (map (fn [{:as route :keys [method]}]
;;               (case method
;;                 ;:post (POST route)
;;                 (GET route))))))


(defn- apply-wrappers [routes wrappers]
  (reduce
   (fn [routes wrapper]
     (wrapper routes))
   routes
   wrappers))


(defn- wrap-routes [routes wrappers app-db]
  (let [config (-> app-db :appconfig/config)
        http-session? (if (contains? config :http-server/http-session?)
                        (-> config :http-server/http-session?)
                        true)
        reload? (-> app-db :appconfig/config :http-server/reload?)
        reload-dirs (into ["src"]
                          (-> config :http-server/reload-dirs))]
    (cond->
      compojure/routes

      true
      (apply routes)

      true
      (apply-wrappers wrappers)

      reload?
      (ring-reload/wrap-reload {:dirs reload-dirs})

      true
      (ring-params/wrap-params)

      http-session?
      (ring-defaults/wrap-defaults
       (-> ring-defaults/site-defaults
           (assoc-in [:session :cookie-attrs :same-site] :lax)))

      (-> "public-web-resources" java.io.File. .exists)
      (ring-file/wrap-file "public-web-resources")

      true
      (ring-resource/wrap-resource "public")

      (-> "target/public" java.io.File. .exists)
      (ring-file/wrap-file "target/public")

      true
      (ring-content-type/wrap-content-type)

      true
      (ring-not-modified/wrap-not-modified))))


(defn start!
  [app-db]
  (let [port (or (-> app-db :appconfig/config :http-server/port)
                 3000)
        routes (-> []
                   (into (routes-from-appmodel))
                   (into (create-default-routes)))
        wrappers (-> []
                     (into (wrappers-from-appmodel app-db)))]
    ;;(tap> [:dbg ::routes routes])
    (tap> [:inf ::start! {:port port}])
    (http-kit/run-server
     (wrap-routes routes wrappers app-db)
     {:port port})
    app-db))


