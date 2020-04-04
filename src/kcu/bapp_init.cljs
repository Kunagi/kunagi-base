(ns kcu.bapp-init
  (:require
   [cljs.reader :refer [read-string]]
   [kcu.tap]
   [kcu.config :as config]))

(tap> [:dbg ::loading])


(defn- load-config []
  (let [browserapp_config (-> js/window .-browserapp_config)
        edn (when browserapp_config (-> browserapp_config .-edn))
        config (when edn (read-string edn))]
    (tap> [:dbg ::load-config config])
    config))


(defonce load-once!
  (do
    (config/set-config! (load-config))
    :done))
