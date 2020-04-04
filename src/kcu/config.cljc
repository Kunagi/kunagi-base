(ns kcu.config)


(defonce CONFIG (atom nil))


(defn set-config! [config]
  (tap> [:inf ::config-set config])
  (swap! CONFIG #(-> {} (merge %) (merge config))))


(defn config []
  @CONFIG)


(defn set-default-config! [default-config]
  (swap! CONFIG #(merge default-config %)))


(defonce SECRETS (atom nil))


(defn set-secrets! [config]
  (tap> [:inf ::secrets-set (count config)])
  (reset! SECRETS config))


(defn secrets []
  @SECRETS)


