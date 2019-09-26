(ns kunagi-base.context
 (:require
  [clojure.spec.alpha :as s]
  [kunagi-base.utils :as utils]))


(s/def ::identifier (= ::identifier))
(s/def ::context (s/keys :req [::identifier]))
(s/def ::app-db-identifer #(= ::app-db-identifier %))
(s/def ::app-db (s/keys :req [::app-db-identifier]))


(defn assert-app-db [db message]
  (utils/assert-spec ::app-db db message))


(defn init-app-db [db]
  (-> db
      (assoc ::app-db-identifier ::app-db-identifier)))


(defonce !app-db (atom {}))


(defn update-app-db
  [update-f]
  (swap! !app-db update-f))


(defn- new-context []
  {::identifier ::identifier})


(defn from-main [args]
  (-> (new-context)
      (assoc :main/args args)
      (assoc :db @!app-db)))


(defn- update-by-http-session [context session]
  (let [user-id (get session :auth/user-id)]
    (-> context
        (merge {:http/session session
                :auth/user-id user-id
                :auth/authenticated? (not (nil? user-id))}))))


(defn from-http-request [req]
  (-> (new-context)
      (update-by-http-session (get req :session))
      (assoc :http/request req)
      (assoc :db @!app-db)))


(defn from-http-async-data [data]
  (-> (from-http-request (-> data :ring-req))
      (assoc :http/client-id (-> data :client-id))
      (assoc :http/client-event (-> data :event))))


(defn from-rf-db [db]
  (let [user (-> db :auth/user)]
    (-> (new-context)
        (assoc :db db)
        (assoc :auth/user-id (-> user :user/id))
        (assoc :auth/user-perms (-> user :user/perms)))))
