(ns kunagi-base-browserapp.appconfig.load-as-browserapp
  (:require
   [cljs.reader :refer [read-string]]
   [kunagi-base.appconfig.api :as appconfig-api]))


(defn- load-config []
  (let [browserapp_config (-> js/window .-browserapp_config)
        edn (when browserapp_config (-> browserapp_config .-edn))
        config (when edn (read-string edn))]
    (tap> [:dbg ::load-config config])
    config))


(defonce load-once!
  (do
    (appconfig-api/set-config! (load-config))
    :done))
