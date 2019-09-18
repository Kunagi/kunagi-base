(ns kunagi-base.auth.c-oauth-users)


(defn sign-in
  [[_ {:keys [service
                                     userinfo
                                     user-id-promise]}]
   context]
  (tap> [:!!! ::sign-in-with-oauth service userinfo (-> context :projections)])
  (let [users-db (-> context :projections :oauth-users)
        sub (-> userinfo :sub)
        oauth-id [service sub]
        user-id (-> users-db :oauth->user (get oauth-id))]
    (if user-id
      {:events [[:user-signed-in {:user-id user-id}]]}
      (let [user-id "new-user-id"]
        {:events [[:user-signed-up {:user-id user-id
                                    :oauth-id oauth-id}]]}))))
