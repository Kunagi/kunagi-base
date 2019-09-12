(ns kunagi-base.context
 (:require
  [clojure.spec.alpha :as s]))


(s/def ::identifier (= ::identifier))
(s/def ::context (s/keys :req [::identifier]))


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


(defn from-reframe-event [db]
  (-> (new-context)
      (assoc :db db)))
