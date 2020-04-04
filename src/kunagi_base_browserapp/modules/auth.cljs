(ns kunagi-base-browserapp.modules.auth
  (:require
   [re-frame.core :as rf]
   [kunagi-base.modules.auth.model]
   [kunagi-base-browserapp.modules.comm-async.model]))


(rf/reg-sub
 :auth/user
 (fn [db _]
   (get-in db [:appconfig/config :auth/user])))
