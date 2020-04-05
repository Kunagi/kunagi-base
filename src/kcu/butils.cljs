(ns kcu.butils
  "Browser Utilities"
  (:require
   [reagent.core :as r]
   [kcu.utils :as u]))


(defn as-local-storage-key [k]
  (if (string? k) k (u/encode-edn k)))


(defn get-from-local-storage [k]
  (-> js/window
      .-localStorage
      (.getItem (as-local-storage-key k))
      u/decode-edn))


(defn set-to-local-storage [k v]
  (-> js/window
      .-localStorage
      (.setItem (as-local-storage-key k)
                (u/encode-edn v))))


(defn durable-ratom [k default-value]
  (let [value (get-from-local-storage k)
        ratom (r/atom (or value default-value))]
    (add-watch ratom ::durable-ratom
               (fn [_ _ old-val new-val]
                 (when (not= old-val new-val)
                   (set-to-local-storage k new-val))))
    ratom))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; headers


(defn install-css [css]
  (let [head (.-head js/document)
        style (.createElement js/document "style")
        text-node (.createTextNode js/document css)]
    (.appendChild style text-node);
    (.appendChild head style)))


(defn install-google-font-link [font]
  (let [head (.-head js/document)
        link (.createElement js/document "link")
        url (str "https://fonts.googleapis.com/css?family=" font)]
    (set! (.-type link) "text/css")
    (set! (.-rel link) "stylesheet")
    (set! (.-href link) url)
    (.appendChild head link)))


(defn install-roboto-font-link []
  (install-google-font-link "Roboto:300,400,500"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; notifications


(defn notifications-info []
  (if-not (exists? js/Notification)
    :not-supported
    {:permission (-> js/Notification .-permission)}))


(defn notifications-supported? []
  (exists? js/Notification))


(defn notifications-permission-granted? []
  (and (notifications-supported?)
       (= "granted" (-> js/Notification .-permission))))


(defn request-notifications-permission [callback]
  (tap> [:inf ::request-notifications-permission])
  (if-not (notifications-supported?)
    (tap> [:wrn ::request-notifications-permission :notifications-not-supported])
    (-> js/Notification
        .requestPermission
        (.then callback))))


(defn- show-notification-via-service-worker [title options]
  (try
    (-> js/navigator
        .-serviceWorker
        .getRegistration
        (.then (fn [registration]
                 (when registration
                   (-> registration (.showNotification title (clj->js options)))))))
    (catch :default ex
      (tap> [:err ::show-notification ex]))))


(defn show-notification [title options]
  (if-not (notifications-supported?)
    (tap> [:wrn ::show-notification :notifications-not-supported])
    (try
      (js/Notification. title (clj->js options))
      (catch :default ex
        (tap> [:dbg ::show-notification ex])
        (show-notification-via-service-worker title options)))))


(defn show-notification-once [identifier title options]
  (let [localstorage-key [:notification-once identifier]]
    (when-not (get-from-local-storage localstorage-key)
      (show-notification title options)
      (set-to-local-storage localstorage-key (u/current-time-millis)))))
