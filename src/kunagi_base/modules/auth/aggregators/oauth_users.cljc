(ns kunagi-base.modules.auth.aggregators.oauth-users
  (:require
   [kunagi-base.utils :as utils]))

(defn sign-in
  [[_ {:keys [service
              userinfo
              return-user-id-f]}]
   context]
  ;;(tap> [:!!! ::sign-in-with-oauth service userinfo (-> context :projections)])
  (let [users-db (-> context :projections :oauth-users)
        sub (-> userinfo :sub)
        oauth-id [service sub]
        user-id (-> users-db :oauth->user (get oauth-id))]
    (if user-id
      {:action-f #(return-user-id-f user-id)}
      (let [user-id (utils/new-uuid)]
        {:events [[:user-signed-up {:user-id user-id :oauth-id oauth-id}]]
         :action-f #(return-user-id-f user-id)}))))
