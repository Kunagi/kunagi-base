(ns kunagi-base-browserapp.subs
  (:require
   [re-frame.core :as rf]))


(rf/reg-sub
 :app/db
 (fn [db _]
   db))


(rf/reg-sub
 :app/info
 (fn [db _]
   (get db :app/info)))


(rf/reg-sub
 :appconfig/config
 (fn [db _]
   (get db :appconfig/config)))


