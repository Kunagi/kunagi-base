(ns kcu.config)


(defonce CONFIG (atom nil))


(defn set-config! [config]
  (tap> [:inf ::config-set config])
  (swap! CONFIG #(-> {} (merge %) (merge config))))


(defn config []
  @CONFIG)


(defn set-default-config! [default-config]
  (swap! CONFIG #(merge default-config %)))


;;; secrets ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce SECRETS (atom nil))


(defn set-secrets! [config]
  (tap> [:inf ::secrets-set (count config)])
  (reset! SECRETS config))


(defn secrets []
  @SECRETS)



;;; appinfo ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn complete-appinfo [appinfo]
  (assoc
   appinfo
   :app-name (or (-> appinfo :app-name)
                 (-> appinfo :project :id)
                 "noname")
   :app-version (or (-> appinfo :app-version)
                    (str (or (-> appinfo :release :major) 0)
                         "."
                         (or (-> appinfo :release :minor) 0)))
   :app-label (or (-> appinfo :app-label)
                  (-> appinfo :project :name)
                  "Noname App")))


(defonce APPINFO (atom (complete-appinfo {})))


(defn appinfo []
  @APPINFO)


(defn set-appinfo [appinfo]
  (reset! APPINFO (complete-appinfo appinfo)))
