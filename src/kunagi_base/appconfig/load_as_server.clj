(ns kunagi-base.appconfig.load-as-server
  (:require
   [kunagi-base.appconfig.api :as appconfig-api]
   [kunagi-base.appconfig.file-loader :as file-loader]))


(defn- load-file [config-name]
  (let [home-path (System/getProperty "user.home")
        app-name (-> home-path (java.io.File.) .getName)]
    (file-loader/load-first-existing-file
     (str config-name ".edn")
     (str home-path "/" config-name ".edn")
     (str "/etc/" app-name "/" config-name ".edn"))))


(defonce load-once!
  (do
    (appconfig-api/set-config (load-file "config"))
    (appconfig-api/set-secrets (load-file "secrets"))
    :done))
