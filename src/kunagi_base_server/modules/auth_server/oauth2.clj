(ns kunagi-base-server.modules.auth-server.oauth2
  (:require
   [ring.middleware.oauth2 :as ring-oauth]

   [kcu.config :as config]
   [kcu.sapp :as sapp]))


(defn decode-jwt
  [token]
  (let [decoder (com.auth0.jwt.JWT/decode token)
        claims (.getClaims decoder)
        keys (.keySet claims)]
    (reduce
     (fn [ret key]
       (let [claim (.get claims key)]
         (if (.isNull claim)
           ret
           (assoc ret (keyword key) (or (.asString claim)
                                        (.asInt claim)
                                        (boolean (.asBoolean claim)))))))
     {}
     keys)))



(defn serve-oauth-completed
  [context]
  ;; (tap> [:!!! :oauth (-> context :http/request)])
  (let [request (-> context :http/request)
        access-tokens (-> request :session :ring.middleware.oauth2/access-tokens)
        service (-> access-tokens keys first)
        tokens-map (get access-tokens service)
        id-token (:id-token tokens-map)
        ;; TODO id-token may be nil -> just redirect
        userinfo (decode-jwt id-token)

        ;; TODO try read-only sign-in first
        !user-id (promise)]

      (sapp/dispatch
       {:command/name :kcu/sign-up-with-oauth
        :service service
        :userinfo userinfo}
       (fn [result]
         (deliver !user-id (get result :user/id))))

      (let [user-id @!user-id] ;; blocking
        (if user-id
          (tap> [:inf ::authenticated user-id])
          (tap> [:inf ::authentication-failed userinfo]))
        {:session {:auth/user-id user-id}
         :status 303
         :headers {"Location" "/"}})))


;;; middleware ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- create-base-config
  [config secrets provider-key provider-specific-config]
  (let [users-config (get-in config [:http-server/oauth provider-key])]
    (when (:enabled? users-config)
      (let [own-uri (get-in config [:http-server/uri])
            prefix (or own-uri "")
            secrets (get-in secrets [provider-key])]
        (if-not secrets
          nil
          (-> {:launch-uri       (str "/oauth/" (name provider-key))
               :redirect-uri     (str prefix "/oauth/" (name provider-key) "/callback")
               :landing-uri      (str "/oauth/completed")
               :basic-auth?      true}
              (merge provider-specific-config)
              (merge users-config)
              (merge secrets)))))))


(defn- create-google-config [config secrets]
  (create-base-config
   config
   secrets
   :google
   {:authorize-uri    "https://accounts.google.com/o/oauth2/v2/auth"
    :access-token-uri "https://www.googleapis.com/oauth2/v4/token"
    :scopes           ["openid" "email" "profile"]}))


(defn- create-ring-oauth2-config
  [config secrets]
  (let [google (create-google-config config secrets)]
    (cond-> {} google (assoc :google google))))


(defn oauth2-wrapper []
  (fn [routes]
    (let [config (config/config)
          secrets (-> (config/secrets) :oauth)]
      (ring-oauth/wrap-oauth2 routes (create-ring-oauth2-config config secrets)))))


