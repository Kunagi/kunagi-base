(ns kunagi-base.appconfig.api)


(defonce !config (atom nil))

(defn set-config [config]
  (tap> [::info :config-set config])
  (reset! !config config))

(defn config []
  @!config)


(defonce !secrets (atom nil))

(defn set-secrets [config]
  (tap> [::info :secrets-set (count config)])
  (reset! !secrets config))

(defn secrets []
  @!secrets)
