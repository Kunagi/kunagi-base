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

   [kunagi-base.appmodel :as am]
   [kunagi-base.context :as context]
   [kunagi-base.auth.api :as auth]
   [kunagi-base-server.modules.assets.api :as assets]
   [kcu.sapp :as sapp]))


(s/def ::route-path string?)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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


;;; appmodel routes


;;; asset route ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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


(defn- wrappers-from-appmodel []
  (let [wrappers (am/q!
                  '[:find ?wrapper-f
                    :where
                    [?r :routes-wrapper/wrapper-f ?wrapper-f]])]
    (map
     (fn [[wrapper-f]]
       (wrapper-f))
     wrappers)))


;;; sente ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- respond-f [data]
  (fn [message]
    (let [send-fn (-> data :send-fn)
          uid (-> data :uid)]
      (send-fn uid message))))


(defn- on-subscription-changed
  [subscription respond-f new-value unsubscribe-f]
  (respond-f [:sapp/subscription-changed {:subscription subscription
                                          :new-value new-value}]))


(defn- on-command-callback
  [command respond-f result]
  (respond-f [:sapp/command-callback {:command-id (-> command :command/id)
                                      :result result}]))


(defonce SENTE-UID->USER-ID (atom {}))


(defn- on-sente-client-connected [data]
  (let [sente-uid (-> data :uid)
        user-id (-> data :ring-req :session :auth/user-id)]
    (tap> [:dbg ::client-connected {:sente-uid sente-uid
                                    :user-id user-id}])
    (swap! SENTE-UID->USER-ID assoc
           sente-uid user-id)
    (when user-id
      ((respond-f data) [:sapp/user-authenticated user-id]))))


(defn- on-sente-client-disconnected [data]
  (let [sente-uid (-> data :uid)
        user-id (-> :ring-req :session :auth/user-id)]
    (tap> [:dbg ::client-disconnected {:sente-uid sente-uid
                                       :user-id user-id}])
    (swap! SENTE-UID->USER-ID dissoc sente-uid)))


(defn- context-from-sente-data [data]
  {:sente data})


(defn- on-data-received [data]
  ;; (tap> [:!!! ::data-received data])

  (case (-> data :id)

    :chsk/ws-ping
    ::nop

    :chsk/uidport-open
    (on-sente-client-connected data)

    :chsk/uidport-close
    (on-sente-client-disconnected data)

    :kcu.bapp/dispatch
    (sapp/dispatch-from-bapp (-> data :event second)
                             (partial on-command-callback
                                      (-> data :event second)
                                      (respond-f data))
                             (context-from-sente-data data))

    :kcu.bapp/subscribe
    (sapp/subscribe (-> data :event second)
                    (partial on-subscription-changed
                             (-> data :event second)
                             (respond-f data))
                    (context-from-sente-data data))

    :kcu.bapp/conversation-messages
    (tap> [:!!! ::conversation-messages-received (-> data :event second)])

    (tap> [:err ::unsupported-async-message-received data])))


(defn- sente-uid-f [request]
  (str (or (-> request :session :auth/user-id)
           "anonymous")
       "/" (-> request :client-id)))


(defn- create-socket
  []
  (let [socket (sente/make-channel-socket!
                (sente-adapter/get-sch-adapter)
                {:user-id-fn sente-uid-f})]
    (sente/start-server-chsk-router!
     (:ch-recv socket)
     #(on-data-received %))
     ;#(when-not (= "chsk" (-> % :id)) on-data-received %))
    socket))


(defonce SENTE-SOCKET (atom nil))


(defn- sente-socket []
  (if-let [socket @SENTE-SOCKET]
    socket
    (let [socket (create-socket)]
      (reset! SENTE-SOCKET socket)
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

   (compojure/GET  "/chsk" [] (fn [req] ((:ajax-get-or-ws-handshake-fn (sente-socket)) req)))
   (compojure/POST "/chsk" [] serve-sente-post)

   ;; (compojure-route/files "/"        {:root "public-web-resources"})
   ;; (compojure-route/resources "/"    {:root "public"})
   (compojure-route/not-found        "404 - Page not found")])



(defn- apply-wrappers [routes wrappers]
  (reduce
   (fn [routes wrapper]
     (wrapper routes))
   routes
   wrappers))


(defn- wrap-routes [routes wrappers]
  (let [config (sapp/config)
        http-session? (if (contains? config :http-server/http-session?)
                        (-> config :http-server/http-session?)
                        true)
        reload? (-> config :http-server/reload?)
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
  []
  (let [port (or (-> (sapp/config) :http-server/port)
                 3000)
        routes (-> []
                   (into (routes-from-appmodel))
                   (into (create-default-routes)))
        wrappers (-> []
                     (into (wrappers-from-appmodel)))]
    ;;(tap> [:dbg ::routes routes])
    (tap> [:inf ::start! {:port port}])
    (http-kit/run-server
     (wrap-routes routes wrappers)
     {:port port})))
