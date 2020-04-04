(ns kunagi-base-browserapp.google-analytics)

(defonce !activated (atom nil))



(defn update-page-path []
  (when-let [tracking-id (->  @!activated :tracking-id)]
    (when (exists? js/gtag)
      (js/gtag "config"
               tracking-id
               (clj->js {"page_path" (-> js/window .-location .-pathname)})))))


(defn track [event-name event-params]
  (when (= "screen_view" event-name)
    (update-page-path))
  (when (exists? js/gtag)
    (js/gtag "event" event-name (clj->js event-params))))


;;; installation

(defonce !installed (atom false))


(defn- install-script-tag [src text]
  (let [script-tag (.createElement js/document "script")]
    (when src
      (set! (.-src script-tag) src))
    (when text
      (set! (.-text script-tag) text))
    (.appendChild (.-head js/document) script-tag)))


(defn install [config]
  (when-not @!installed
    (tap> [:dbg ::install-script config])
    (install-script-tag
     (str "https://www.googletagmanager.com/gtag/js?id=" (-> config :tracking-id))
     nil)
    (let [gt-config {;; "send_page_view" false
                     "anonymize_ip" (get config :anonymize-ip true)}
          gt-config-s (.stringify js/JSON (clj->js gt-config))
          gt-set {"app_name" (get config :app-name "no_app_name")
                  "app_id" (get config :app-id "no_app_id")
                  "app_version" (get config :app-version "0")
                  "currency" (get config :currency "EUR")}
          gt-set-s (.stringify js/JSON (clj->js gt-set))]
      (install-script-tag
       nil
       (str "
  window.dataLayer = window.dataLayer || [];
  function gtag(){console.log('gtag()', arguments); dataLayer.push(arguments);}
  gtag('js', new Date());

  gtag('config', '" (-> config :tracking-id) "', " gt-config-s ");
  gtag('set', " gt-set-s ");
")))
    ;; (set! (.-onhashchange js/window)
    ;;       #(update-page-path (-> js/window .-location .-pathname)))
    (reset! !installed true)))


;;; activate

(defn activate [config]
  (when-not @!activated
    (tap> [:inf ::activate])
    (install config)
    (reset! !activated config)))


;;; deactivate


(defn deactivate []
  (when @!activated
    (tap> [:inf ::deactivate])
    (install-script-tag nil (str "window['ga-disable-"
                                 (-> @!activated :tracking-id)
                                 "'] = true;"))
    (reset! !activated nil)))
