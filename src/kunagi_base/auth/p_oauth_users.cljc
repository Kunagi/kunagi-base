(ns kunagi-base.auth.p-oauth-users)


(defn- new-db []
  {})


(defn- user-signed-up [db [_ {:keys [user-id oauth-id]}]]
  (assoc-in db [:oauth->user oauth-id] user-id))


(defn apply-event [db event]
  (if-let [f (case (first event)
               :user-signed-up user-signed-up
               nil)]
    (-> db
        (or (new-db))
        (f event))))
