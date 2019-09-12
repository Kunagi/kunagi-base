(ns kunagi-base.cqrs.queries
  (:require
   [kunagi-base.cqrs.api :as cqrs :refer [def-query-responder]]
   [kunagi-base.appconfig.api :as appconfig]
   [kunagi-base.auth.users-db :as users-db]))


(def-query-responder
  ::config
  :debug/ping
  (fn [context query]
    [{:pong query}]))


(def-query-responder
  ::config
  :appconfig/config
  (fn [_ _]
    [(appconfig/config)]))


(def-query-responder
  ::config
  :appconfig/secrets
  (fn [_ _]
    [(appconfig/secrets)]))


;;; auth

(def-query-responder
  ::user-id-by-google-email
  :auth/user-id-by-google-email
  (fn [context [_ google-email]]
    (when-let [users-db (-> context :db :users-db)]
      [(users-db/user-id-by-google-email users-db google-email)])))

(def-query-responder
  ::user-for-browserapp
  :auth/user--for-browserapp
  (fn [context [_ user-id]]
    (when-let [users-db (-> context :db :users-db)]
      (when-let [user (users-db/user--for-browserapp users-db (-> context :auth/user-id))]
        [user]))))
