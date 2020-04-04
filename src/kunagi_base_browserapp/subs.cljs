(ns kunagi-base-browserapp.subs
  (:require
   [re-frame.core :as rf]
   [kcu.config :as config]))


(rf/reg-sub
 :app/db
 (fn [db _]
   db))


(rf/reg-sub
 :app/info
 (fn [db _]
   (config/appinfo)))


(rf/reg-sub
 :appconfig/config
 (fn [db _]
   (config/config)))
