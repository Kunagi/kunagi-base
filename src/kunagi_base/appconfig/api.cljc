(ns kunagi-base.appconfig.api)


(defonce !config (atom nil))


(defn set-config! [config]
  (tap> [:inf ::config-set config])
  (swap! !config #(-> {} (merge %) (merge config))))


(defn config []
  @!config)


(defn set-default-config! [default-config]
  (swap! !config #(merge default-config %)))


(defonce !secrets (atom nil))


(defn set-secrets! [config]
  (tap> [:inf ::secrets-set (count config)])
  (reset! !secrets config))


(defn secrets []
  @!secrets)


(defn init-app-db [db]
  (-> db
      (assoc :appconfig/config (config))
      (assoc :appconfig/secrets-f secrets)))
