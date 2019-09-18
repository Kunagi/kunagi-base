(ns kunagi-base.auth.p-oauth-users)


(defn- new-db []
  {:oauth->user {}})


(defn- user-signed-up [db [_ {:keys [user-id oauth-id]}]]
  (assoc-in db [:oauth->user oauth-id] user-id))


(defn- unknown-event [db event]
  (tap> [:!!! ::unknown-event event]))


(defn apply-event [db event]
  (tap> [:!! ::event event db])
  (if-let [f (case (first event)
               :user-signed-up user-signed-up
               unknown-event)]
    (-> db
        (or (new-db))
        (f event))))
