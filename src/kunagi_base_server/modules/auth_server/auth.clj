(ns kunagi-base-server.modules.auth-server.auth
  (:require


   [kunagi-base.modules.event-sourcing.api :as es]))


(defn complete-user-for-browserapp-by-oauth-userinfos [user context]
  (let [user-id (-> user :user/id)
        oauth-users (es/projection context
                                   [:auth/oauth-users "singleton"]
                                   :oauth-users)
        [service sub] (-> oauth-users :user->oauth (get user-id))
        userinfos (es/projection context
                                 [:auth/oauth-userinfos "singleton"]
                                 :oauth-userinfos)
        userinfo (-> userinfos :service->sub->userinfo (get service) (get sub))
        user (assoc user :user/oauth-userinfo userinfo)]
    user))


(defn user--for-browserapp [context]
  (when-let [user-id (-> context :auth/user-id)]
    (let [user-name user-id]
      (-> {:user/id user-id
           :user/perms (-> context :auth/user-perms)}
          (complete-user-for-browserapp-by-oauth-userinfos context)))))


(defn serve-sign-out [context]
  {:session nil
   :status 303
   :headers {"Location" "/"}})


