(ns kunagi-base.auth.p-oauth-userinfos)


(defn- new-db []
  {:oauth->user {}})


(defn- oauth-userinfo-received
  [db [_ {:keys [service userinfo]}]]
  (if-let [sub (-> userinfo :sub)]
    (update-in db [:oauth->user service sub] merge userinfo)
    db))


(defn apply-event [db event]
  (tap> [:!!! ::apply-event event])
  (if-let [f (case (first event)
               :oauth-userinfo-received oauth-userinfo-received
               nil)]
    (-> db
        (or (new-db))
        (f event))))
