(ns kunagi-base.modules.startup.api
  (:require
   [clojure.spec.alpha :as s]
   #?(:cljs [cljs.reader :refer [read-string]])
   [re-frame.core :as rf]

   [kunagi-base.utils :as utils]
   [kunagi-base.context :as context]
   [kunagi-base.appconfig.api :as appconfig]
   [kunagi-base.appmodel :as am]))


(s/def :app/db-identifier #(= :app/db-identifier %))
(s/def :app/db (s/keys :req [:app/db-identifier]))


(defn- exec-init-function [app-db [id f]]
  (tap> [:dbg ::run-init-function id])
  (-> app-db
      f
      (context/assert-app-db (str "Init function " id " failed to return a valid db."))))


(defn exec-init-functions [app-db]
  (reduce
   exec-init-function
   app-db
   (am/q! '[:find ?id ?f
            :where
            [?e :init-function/id ?id]
            [?e :init-function/f ?f]])))


(defn- -init-app-db [app-db initial-data]
  (tap> [:dbg ::init-app-db initial-data])
  (-> app-db
      (assoc :app/db-identifier :app/db-identifier)
      (context/init-app-db)
      (merge initial-data)
      (exec-init-functions)))


(defn- log-environment-info! []
  #?(:clj
     (tap> [:inf ::environment-info
            {:working-directory (System/getProperty "user.dir")
             :user-name (System/getProperty "user.name")
             :user-home (System/getProperty "user.home")
             :java-version (System/getProperty "java.version")
             :locale (-> (java.util.Locale/getDefault) .toString)}])))


(defn start! [initial-data]
  (log-environment-info!)
  (let [db (-> {}
               (utils/deep-merge initial-data)
               appconfig/init-app-db)]
    #?(:clj  (context/update-app-db #(-init-app-db % db))
       :cljs (rf/dispatch-sync [::init db]))))


;;; re-frame

(rf/reg-event-db
 ::init
 (fn [db [_ initial-data]]
   (-init-app-db db initial-data)))
