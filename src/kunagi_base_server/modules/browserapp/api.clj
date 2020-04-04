(ns kunagi-base-server.modules.browserapp.api
  (:require
   [html-tools.htmlgen :as htmlgen]

   [kunagi-base-server.modules.auth-server.auth :as auth]))


(defn- browserapp-config [req context]
  (-> {}
      (merge (-> context :db :appconfig/config :browserapp/config))
      (assoc :auth/user (auth/user--for-browserapp context))
      (assoc :serverapp/info (-> context :db :app/info))))


(defn serve-app [context]
  (let [v (-> context :http/request :params (get "v"))
        app-info (-> context :db :app/info)
        config (-> context :db :appconfig/config)
        lang (or (-> config :browserapp/lang) "en")
        ;; google-analytics-tracking-id (-> config :google-analytics/tracking-id)
        cookie-consent-script-url (-> config :browserapp/cookie-consent-script-url)
        standalone? (-> app-info :browserapp :standalone?)
        head-contents []
        head-contents (if cookie-consent-script-url
                        (conj head-contents [:script {:src cookie-consent-script-url}])
                        head-contents)
        head-contents (if-let [google-adsense-code (-> config :browserapp/google-adsense-code)]
                        (conj head-contents [:script
                                             {:data-ad-client google-adsense-code
                                              :async true
                                              :src "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"}])
                        head-contents)
        head-contents (if standalone?
                        ;; https://medium.com/appscope/designing-native-like-progressive-web-apps-for-ios-1b3cdda1d0e8
                        (into head-contents
                              [[:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
                               [:meta {:name "apple-mobile-web-app-title" :content (-> app-info :app-label)}]
                               [:link {:rel "apple-touch-icon" :href "/apple-touch-icon.png"}]])
                        head-contents)
        favicon? (if (contains? config :browserapp/favicon?)
                   (-> config :browserapp/favicon?)
                   true)
        head-contents (if favicon?
                        (conj head-contents [:link {:rel "icon"
                                                    :type "image/png"
                                                    :href "/favicon.png"}])
                        head-contents)
        error-alert? (-> config :browserapp/error-alert?)
        modules (cond-> [:browserapp :manifest-json]
                  error-alert? (conj :error-alert))]

    (htmlgen/page-html
     (-> context :http/request)
     {:modules modules
      :lang lang
      :head-contents head-contents
      :browserapp-config-f #(browserapp-config % context)
      :js-build-name (-> context :db :appconfig/config :browserapp/js-build-name)
      :js-build-v v
      :browserapp-name (-> app-info :app-name)
      :title (-> app-info :app-label)})))
      ;;:google-analytics-tracking-id google-analytics-tracking-id})))


(defn serve-redirect-to-app [context]
  {:status 301 :headers {"Location" "/ui/"}})
