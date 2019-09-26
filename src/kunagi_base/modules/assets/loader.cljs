(ns kunagi-base.modules.assets.loader
  (:require
   [cljs.reader :as reader]
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [kunagi-base.appmodel :as appmodel]))

;; TODO move to kunagi-base-browserapp


(defn- ajax-error-handler [asset-pool-id asset-path url]
  (fn [error]
    (rf/dispatch
     [::asset-request-failed
      {:asset-pool-id asset-pool-id
       :asset-path asset-path
       :url url
       :error error}])))


(defn- ajax-success-handler [asset-pool-id path]
  (fn [response]
    (rf/dispatch
     [:assets/asset-received
      {:asset-pool-id asset-pool-id
       :path path
       :data (reader/read-string response)}])))


(defn- ajax-url [asset-pool [_ _ asset-path :as path]]
  (if-let [dir-path (-> asset-pool :asset-pool/dir-path)]
    (str "/" dir-path "/" asset-path)
    (str "/api/asset?edn="
         (pr-str path))))


(defn ajax-load-f [db asset-pool asset-path]
  (let [asset-pool-id (:db/id asset-pool)
        asset-pool-ident (:asset-pool/ident asset-pool)
        module-ident (-> asset-pool :asset-pool/module :module/ident)
        path [module-ident asset-pool-ident asset-path]
        url (ajax-url asset-pool path)]
    (ajax/GET url
              {:handler (ajax-success-handler asset-pool-id path)
               :error-handler (ajax-error-handler asset-pool-id asset-path url)})
    nil))


(defn comm-async-load-f [db asset-pool asset-path]
  (let [asset-pool-id (:db/id asset-pool)
        asset-pool-ident (:asset-pool/ident asset-pool)
        module-ident (-> asset-pool :asset-pool/module :module/ident)
        path [module-ident asset-pool-ident asset-path]]
    (rf/dispatch [:comm-async/send-event [:assets/asset-requested path]])
    nil))


(defn default-load-f [db asset-pool asset-path]
  (if (-> asset-pool :asset-pool/dir-path)
    (ajax-load-f db asset-pool asset-path)
    (comm-async-load-f db asset-pool asset-path)))


(rf/reg-event-db
 :assets/asset-received
 (fn [db [_ {:keys [path
                    data]}]]
   (tap> [:dbg ::asset-received path data])
   (let [[module-ident asset-pool-ident asset-path] path
         db-path [:assets module-ident asset-pool-ident asset-path]]
     (assoc-in db db-path data))))


(rf/reg-event-db
 ::asset-request-failed
 (fn [db [_ {:keys [asset-pool-id
                    asset-path
                    url
                    error]}]]
   (let [asset-pool (appmodel/entity! asset-pool-id)
         module-ident (-> asset-pool :asset-pool/module :module/ident)
         asset-pool-ident (:asset-pool/ident asset-pool)
         error (merge error
                      {:module-ident module-ident
                       :asset-pool-ident asset-pool-ident
                       :url url})]
     (tap> [:wrn ::resource-request-failed error])
     (assoc-in db
               [:assets module-ident asset-pool-ident asset-path]
               [:resource/error error]))))


(rf/reg-event-db
 :assets/asset-update-requested
 (fn [db [_ [module-ident asset-pool-ident asset-path]]]
   db))
