(ns kunagi-base.cqrs.queries
  (:require
   [facts-db.api :as db]

   [kunagi-base.cqrs.api :as cqrs :refer [def-query-responder]]
   [kunagi-base.appconfig.api :as appconfig]
   [kunagi-base.auth.users-db :as users-db]))


(def-query-responder
  :debug/ping
  ::ident
  (fn [context query]
    [{:pong query}]))


(def-query-responder
  :appconfig/config
  ::ident
  (fn [_ _]
    [(appconfig/config)]))


(def-query-responder
  :appconfig/secrets
  ::ident
  (fn [_ _]
    [(appconfig/secrets)]))


;;; auth

(def-query-responder
  :auth/user-id-by-google-email
  ::ident
  (fn [context [_ google-email]]
    (when-let [users-db (-> context :db :users-db)]
      [(users-db/user-id-by-google-email users-db google-email)])))

(def-query-responder
  :auth/user--for-browserapp
  ::ident
  (fn [context _]
    (when-let [users-db (-> context :db :users-db)]
      (when-let [user (db/query users-db [:user--for-browserapp (-> context :auth/user-id)])]
                 ;;(users-db/user--for-browserapp users-db (-> context :auth/user-id))]
        [user]))))
