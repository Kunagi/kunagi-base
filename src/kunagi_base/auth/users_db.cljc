(ns kunagi-base.auth.users-db
  (:require
   [facts-db.api :as db :refer [def-api def-event def-query]]))


(def root-id "users.index")


(def-api ::users-db
  :db-constructor
  (fn [config]
    [{:db/id root-id
      :index/users []}]))

(defn new-db []
  (-> (db/new-db :users-db {})))


(def apply-events db/apply-events)


(defn user-id-by-google-email [db google-email]
  (-> db
      (db/tree root-id {:index/users {}})
      :index/users
      (->> (filter (fn [user] (= google-email (get user :user/google-email))))
           first
           :db/id)))


(defn user--for-browserapp [db user-id]
  (db/tree db user-id {}))


;;; queries

(def-query ::user--for-browserapp user--for-browserapp)


;;; events

(def-event ::user-registered
  (fn [db {:keys [id name google-email]}]
      [{:db/id id
        :user/name name
        :user/google-email google-email
        :db/add-ref-n [root-id :index/users]}]))


;;; api
