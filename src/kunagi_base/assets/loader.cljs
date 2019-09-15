(ns kunagi-base.assets.loader
  (:require
   [cljs.reader :as reader]
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [kunagi-base.appmodel :as appmodel]))


(defn default-load-f [db asset-pool asset-path]
  (let [asset-pool-id (:db/id asset-pool)
        asset-pool-ident (:asset-pool/ident asset-pool)
        module-ident (-> asset-pool :asset-pool/module :module/ident)
        path [module-ident asset-pool-ident asset-path]
        url (str "/api/asset?edn=" (pr-str path))]
    (ajax/GET url
              {
               :handler
               (fn [response]
                 (rf/dispatch
                  [::asset-received
                   {:asset-pool-id asset-pool-id
                    :asset-path asset-path
                    :data (reader/read-string response)}]))

               :error-handler
               (fn [error]
                 (rf/dispatch
                  [::asset-request-failed
                   {:asset-pool-id asset-pool-id
                    :asset-path asset-path
                    :url url
                    :error error}]))})
    nil))


(rf/reg-event-db
 ::asset-received
 (fn [db [_ {:keys [asset-pool-id
                    asset-path
                    data]}]]
   (tap> [:dbg ::asset-received data])
   (let [asset-pool (appmodel/entity! asset-pool-id)
         module-ident (-> asset-pool :asset-pool/module :module/ident)
         asset-pool-ident (:asset-pool/ident asset-pool)]
     (assoc-in db [:assets module-ident asset-pool-ident asset-path] data))))


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
