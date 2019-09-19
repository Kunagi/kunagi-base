(ns kunagi-base.modules.startup.api
  (:require
   #?(:cljs [cljs.reader :refer [read-string]])
   [re-frame.core :as rf]

   [kunagi-base.utils :as utils]
   [kunagi-base.context :as context]
   [kunagi-base.assets :as assets]
   [kunagi-base.appmodel :as am]))


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
      (context/init-app-db)
      (merge initial-data)
      (assets/load-startup-assets)
      (exec-init-functions)))


(defn start! [initial-data]
  #?(:clj (context/update-app-db #(-init-app-db % initial-data))
     :cljs (let [initial-data (if (string? initial-data)
                                (read-string initial-data)
                                initial-data)]
             (rf/dispatch-sync [::init initial-data]))))


;;; re-frame

(rf/reg-event-db
 ::init
 (fn [db [_ initial-data]]
   (-init-app-db db initial-data)))
