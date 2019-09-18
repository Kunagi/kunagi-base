(ns kunagi-base.auth.p-oauth-users)


(defn- new-db []
  {:oauth->user {}
   :user->oauth {}})


(defn- user-signed-up [db [_ {:keys [user-id oauth-id]}]]
  (-> db
      (assoc-in [:oauth->user oauth-id] user-id)
      (assoc-in [:user->oauth user-id] oauth-id)))


(defn- unknown-event [db event]
  (tap> [:!!! ::unknown-event event]))


(defn apply-event [db event]
  (if-let [f (case (first event)
               :user-signed-up user-signed-up
               unknown-event)]
    (-> db
        (or (new-db))
        (f event))))
