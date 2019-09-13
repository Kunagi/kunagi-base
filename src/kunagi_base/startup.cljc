(ns kunagi-base.startup
  (:require
   [kunagi-base.resources :as resources]
   [kunagi-base.appmodel :as appmodel]))


(defn init-functions [pull-template]
  (appmodel/entities :index/init-functions pull-template))


(defn def-init-function [init-function]
  (appmodel/register-entity
   :init-function
   init-function))


(defn- exec-init-function [app-db init-function]
  (tap> [:dbg ::run-init-function init-function])
  ;; TODO assert result is app-db
  ((:init-function/f init-function) app-db))


(defn exec-init-functions [app-db]
  (reduce exec-init-function app-db (init-functions {})))


(defn init-app-db [app-db]
  (-> app-db
      (resources/load-resources)
      (exec-init-functions)))
