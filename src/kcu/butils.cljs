(ns kcu.butils
  "Browser Utilities"
  (:require
   [reagent.core :as r]
   [ajax.core :as ajax]
   [cemerick.url :as camerick-url]

   [kcu.utils :as u]))


;;; utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn reload-page []
  (js/location.reload))


(defn navigate-to [href]
  (set! (.-location js/window) href))


(defn combi-ratom [input-ratoms update-f]
  (let [ratom (r/atom nil)
        update-ratom (fn []
                       (reset! ratom (update-f (mapv deref input-ratoms))))]
    (doseq [input-ratom input-ratoms]
      (add-watch input-ratom
                 [::combi-ratom ratom]
                 (fn [_ _ _ _]
                   (update-ratom))))
    (update-ratom)
    ratom))


;;; URLs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def url-encode camerick-url/url-encode)


(defn url-encode-path+args [path parameters]
  (str (url-encode path)
       (reduce
        (fn [s [k v]]
          (str s
               (if (empty? s) "?" "&")
               (url-encode (name k))
               "="
               (url-encode v)))
        ""
        parameters)))

;;; AJAX ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn GET-edn [url callback]
  (tap> [:dbg ::GET-edn url])
  (ajax/GET
   url
   {:handler (fn [response]
               (tap> [:dbg ::GET-edn-received url response])
               (callback (u/decode-edn response) nil))
    :error-handler (fn [response]
                     (tap> [:wrn ::GET-edn-failed url response])
                     (callback nil response))}))


(defn remote-edn-ratom
  ([url]
   (remote-edn-ratom url nil nil))
  ([url initial-value convert-f]
   (let [ratom (r/atom initial-value)]
     (GET-edn url (fn [value error]
                    (when value
                      (let [value (if convert-f
                                    (convert-f value)
                                    value)]
                        (reset! ratom value)))))
     ratom)))


;;; localStorage ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn as-local-storage-key [k]
  (if (string? k) k (u/encode-edn k)))


(defn get-from-local-storage [k]
  (try
    (-> js/window
        .-localStorage
        (.getItem (as-local-storage-key k))
        u/decode-edn)
    (catch :default ex
      (tap> [:err ::get-from-local-storage-failed
             {:key k
              :storageKey (as-local-storage-key k)
              :exception ex}])
      nil)))


(defn set-to-local-storage [k v]
  (-> js/window
      .-localStorage
      (.setItem (as-local-storage-key k)
                (u/encode-edn v))))


(defn subscribe-to-local-storage [k callback]
  (-> js/window
      (.addEventListener
       "storage"
       (fn [event]
         (when (= (as-local-storage-key k)
                  (-> event .-key))
           (callback (u/decode-edn (-> event .-newValue))))))))


(defn subscribe-to-local-storage-clear [callback]
  (-> js/window
      (.addEventListener
       "storage"
       (fn [event]
         (when-not (-> event .-key)
           (callback))))))


(defonce DURABLE-RATOMS (atom {}))


(defn durable-ratom
  ([k]
   (durable-ratom k nil))
  ([k default-value]
   (if-let [ratom (get @DURABLE-RATOMS k)]
     ratom
     (let [value (get-from-local-storage k)
           ratom (r/atom (or value default-value))]
       (add-watch ratom ::durable-ratom
                  (fn [_ _ old-val new-val]
                    (when (not= old-val new-val)
                      (set-to-local-storage k new-val))))
       (subscribe-to-local-storage
        k (fn [new-val]
            (when-not (= new-val @ratom)
              (tap> [:inf ::durable-ratom-changed-in-local-storage k])
              (reset! ratom new-val))))
       (swap! DURABLE-RATOMS assoc k ratom)
       ratom))))


(defn clear-local-storage []
  (-> js/window .-localStorage .clear))


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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn clear-all-data []
  ;; TODO clear db
  ;; TODO clear cookies
  (clear-local-storage))


(defn clear-all-data-and-reload-page []
  (clear-all-data)
  (reload-page))
