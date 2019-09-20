(ns kunagi-base.modules.assets.loader
  (:require
   [cljs.reader :as reader]
   [re-frame.core :as rf]
   [kunagi-base.appmodel :as appmodel]))

;; TODO move to kunagi-base-browserapp

(defn default-load-f [db asset-pool asset-path]
  (let [asset-pool-id (:db/id asset-pool)
        asset-pool-ident (:asset-pool/ident asset-pool)
        module-ident (-> asset-pool :asset-pool/module :module/ident)
        path [module-ident asset-pool-ident asset-path]]
    (rf/dispatch [:comm-async/send-event [:assets/asset-requested path]])
    nil))


;; (defn default-load-f [db asset-pool asset-path]
;;   (let [asset-pool-id (:db/id asset-pool)
;;         asset-pool-ident (:asset-pool/ident asset-pool)
;;         module-ident (-> asset-pool :asset-pool/module :module/ident)
;;         path [module-ident asset-pool-ident asset-path]
;;         url (str "/api/asset?edn=" (pr-str path))]
;;     (ajax/GET url
;;               {
;;                :handler
;;                (fn [response]
;;                  (rf/dispatch
;;                   [:assets/asset-received
;;                    {:asset-pool-id asset-pool-id
;;                     :asset-path asset-path
;;                     :data (reader/read-string response)}]))

;;                :error-handler
;;                (fn [error]
;;                  (rf/dispatch
;;                   [::asset-request-failed
;;                    {:asset-pool-id asset-pool-id
;;                     :asset-path asset-path
;;                     :url url
;;                     :error error}]))})
;;     nil))


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
