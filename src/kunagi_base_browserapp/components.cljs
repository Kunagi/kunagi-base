(ns kunagi-base-browserapp.components
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]
   [mui-commons.components :as muic]
   [mui-commons.api :refer [<subscribe]]
   [kunagi-base-browserapp.subs]))


(defn- user-has-permission? [req-perm]
  (when-let [user (<subscribe [:auth/user])]
    (let [user-perms (or (-> user :user/perms)
                         #{})]
      (user-perms req-perm))))


(defn DevModeBoundary [& dev-only-components]
  (let [config (<subscribe [:appconfig/config])
        dev-mode? (-> config :dev-mode?)]
    (into
     [:div.DevModeBoundary]
     (when dev-mode? dev-only-components))))



(defn PermissionBoundary [req-perm protected-component alternative-component]
  [:div.PermsBoundary
   (if (user-has-permission? req-perm)
     protected-component
     alternative-component)])

