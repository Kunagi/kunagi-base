(ns kcu.sapp-init
  (:require
   [clojure.java.io :as io]
   [kcu.tap-formated]
   [kcu.files :as files]
   [kcu.config :as config]))


(tap> [:dbg ::loading])


(defn- load-first-existing-file [& files]
  (reduce
   (fn [prev-result file]
     (if prev-result
       prev-result
       (when-let [data (files/read-edn file)]
         (tap> [:inf ::config-file-loaded (.getAbsolutePath file)])
         data)))
   nil
   (map io/as-file files)))


(defn- load-config-file [config-name]
  (let [home-path (System/getProperty "user.home")
        app-name (-> home-path (java.io.File.) .getName)]
    (load-first-existing-file
     (str config-name ".edn")
     (str home-path "/" config-name ".edn")
     (str "/etc/" app-name "/" config-name ".edn"))))


(defonce load-once!
  (do
    (config/set-config! (load-config-file "config"))
    (config/set-secrets! (load-config-file "secrets"))
    :done))
