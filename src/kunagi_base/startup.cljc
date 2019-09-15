(ns kunagi-base.startup
  (:require
   #?(:cljs [cljs.reader :refer [read-string]])
   [re-frame.core :as rf]

   [kunagi-base.context :as context]
   [kunagi-base.assets :as assets]
   [kunagi-base.appmodel :as appmodel]))


(defn init-functions [pull-template])
  ;; FIXME(appmodel/entities (appmodel/model) :index/init-functions pull-template))


(defn def-init-function [init-function]
  (appmodel/register-entity
   :init-function
   init-function))


(defn- exec-init-function [app-db [ident f]]
  (tap> [:dbg ::run-init-function ident])
  ;; TODO assert result is app-db
  (f app-db))


(defn exec-init-functions [app-db]
  (reduce
   exec-init-function
   app-db
   (appmodel/q! '[:find ?ident ?f
                  :where
                  [?e :init-function/ident ?ident]
                  [?e :init-function/f ?f]])))


(defn- -init-app-db [app-db initial-data]
  (tap> [:dbg ::init-app-db initial-data])
  (-> app-db
      (assoc ::identifier ::identifier)
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
