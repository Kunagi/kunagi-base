(ns kunagi-base.context
 (:require
  [clojure.spec.alpha :as s]))


(s/def ::identifier (= ::identifier))
(s/def ::context (s/keys :req [::identifier]))


(defn new-context []
  {::identifier ::identifier})


(defn from-http-session [session]
  (let [user-id (get session :auth/user-id)]
    (-> (new-context)
        (merge {:http/session session
                :auth/user-id user-id
                :auth/authenticated? (not (nil? user-id))}))))


(defn from-http-request [req]
  (-> (from-http-session (get req :session))
      (assoc :http/request req)))
