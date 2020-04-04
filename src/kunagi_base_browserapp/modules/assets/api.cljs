(ns kunagi-base-browserapp.modules.assets.api
  (:require
   [cljs.reader :as reader]
   [re-frame.core :as rf]
   [ajax.core :as ajax]

   [kunagi-base.utils :as utils]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.context :as context]
   [kunagi-base.appmodel :as am]
   [kunagi-base-browserapp.modules.assets.localstorage :as localstorage]))

;;;

(defn- on-asset-updated [asset-pool asset-path asset]
  (localstorage/store-asset asset-pool asset-path asset))


;;; api

(defn asset [db asset-pool-ident asset-path]
  (get-in db [:assets/asset-pools asset-pool-ident asset-path]))


(defn set-asset [db asset-pool-ident asset-path new-value]
  ;; TODO assert spec of asset
  (let [old-value (asset db asset-pool-ident asset-path)]
    (if (= old-value new-value)
      db
      (let [asset-pool (am/entity! [:asset-pool/ident asset-pool-ident])]
        (on-asset-updated asset-pool asset-path new-value)
        (assoc-in db [:assets/asset-pools asset-pool-ident asset-path] new-value)))))


(defn update-asset [db asset-pool-ident asset-path update-f]
  (tap> [:dbg ::update-asset asset-pool-ident asset-path update-f])
  (let [value (asset db asset-pool-ident asset-path)]
    (set-asset db asset-pool-ident asset-path (update-f value))))
  ;; (if-let [asset (asset db asset-pool-ident asset-path)]
  ;;   (set-asset db asset-pool-ident asset-path (update-f asset))
  ;;   (throw (ex-info (str "Asset "
  ;;                        (pr-str [asset-pool-ident asset-path])
  ;;                        " is not loaded. Updating failed.")
  ;;                   {:asset-pool-ident asset-pool-ident
  ;;                    :asset-path asset-path}))))


(defn- handle-asset-received [db asset-pool asset-path data]
  (let [handlers (-> asset-pool :asset-pool/asset-received-handlers)]
    (reduce
     (fn [db handler]
       (utils/assert-spec
        :app/db
        (handler db asset-pool asset-path data)))
     db
     handlers)))


;;; re-frame


(rf/reg-sub
 :assets/asset-pool
 (fn [db [_ asset-pool-ident]]
   (get-in db [:assets/asset-pools asset-pool-ident])))


(rf/reg-sub
 :assets/asset
 (fn [db [_ asset-pool-ident asset-path]]
   (asset db asset-pool-ident asset-path)))


(rf/reg-event-db
 :assets/asset-received
 (fn [db [_ {:keys [asset-pool-ident
                    asset-path
                    data]}]]
   (tap> [:dbg ::asset-received asset-pool-ident asset-path])
   (let [asset-pool (am/entity! [:asset-pool/ident asset-pool-ident])
         transformer (-> asset-pool :asset-pool/asset-received-transformer)
         data (if-not transformer
                data
                (transformer asset-pool asset-path data))]
       (-> db
           (set-asset asset-pool-ident asset-path data)
           (handle-asset-received asset-pool asset-path data)))))


(rf/reg-event-db
 ::asset-request-failed
 (fn [db [_ {:keys [asset-pool-ident
                    asset-path
                    url
                    error]}]]
   (let [error (merge error
                      {:asset-pool-ident asset-pool-ident
                       :asset-path asset-path
                       :url url})]
     (tap> [:wrn ::asset-request-failed error])
     ;; TODO display error to user
     db)))


(defn- ajax-error-handler [asset-pool-ident asset-path url]
  (fn [error]
    (rf/dispatch
     [::asset-request-failed
      {:asset-pool-ident asset-pool-ident
       :asset-path asset-path
       :url url
       :error error}])))


(defn- ajax-success-handler [asset-pool-ident asset-path]
  (fn [response]
    (rf/dispatch
     [:assets/asset-received
      {:asset-pool-ident asset-pool-ident
       :asset-path asset-path
       :data (reader/read-string response)}])))


(defn- ajax-url [asset-pool asset-path]
  (if-let [url-path (-> asset-pool :asset-pool/url-path)]
    (str "/" url-path "/" asset-path)
    (str "/api/asset?edn="
         (pr-str [(-> asset-pool :asset-pool/ident) asset-path]))))


(defn- request-asset-via-ajax! [asset-pool asset-path]
  (let [asset-pool-ident (-> asset-pool :asset-pool/ident)
        url (ajax-url asset-pool asset-path)]
    (ajax/GET url
              {:handler (ajax-success-handler asset-pool-ident asset-path)
               :error-handler (ajax-error-handler asset-pool-ident asset-path url)})))


(defn- request-asset-via-comm-async! [asset-pool asset-path]
  (let [asset-pool-ident (-> asset-pool :asset-pool/ident)]
    (rf/dispatch [:comm-async/send-event
                  [:assets/asset-requested asset-pool-ident asset-path]])))


(defn- request-asset-from-pool! [asset-pool asset-path]
  (if (-> asset-pool :asset-pool/url-path)
    (request-asset-via-ajax! asset-pool asset-path)
    (request-asset-via-comm-async! asset-pool asset-path)))


(defn request-asset! [asset-ident asset-path]
  (request-asset-from-pool! (am/entity! [:asset-pool/ident asset-ident]) asset-path))


(defn- request-startup-assets-from-pool
  [asset-pool context]
  (let [req-perms (-> asset-pool :asset-pool/req-perms)]
    (if (auth/context-has-permissions? context req-perms)
      (doseq [asset-path (-> asset-pool :asset-pool/request-on-startup)]
        (request-asset-from-pool! asset-pool asset-path))
      (tap> [:wrn ::request-startup-assets-from-pool_no-permission {:req-perms req-perms
                                                                    :context context}]))))


(defn- q-asset-pools-with-request-on-startup []
  '[:find ?e
    :where
    [?e :asset-pool/request-on-startup _]])


(defn request-startup-assets [app-db]
  ;; (tap> [:!!! ::request-startup-assets])
  (doseq [[asset-pool-id] (am/q! (q-asset-pools-with-request-on-startup))]
    (request-startup-assets-from-pool (am/entity! asset-pool-id)
                                      (context/from-rf-db app-db)))
  app-db)


(defn- load-asset-from-pool [db asset-pool asset-path]
  (tap> [:dbg ::load-asset-from-pool (-> asset-pool :asset-pool/ident) asset-path])
  (set-asset db
             (-> asset-pool :asset-pool/ident)
             asset-path
             (localstorage/load-asset asset-pool asset-path)))


(defn load-asset [db asset-pool-ident asset-path]
  (let [asset-pool (am/entity! [:asset-pool/ident asset-pool-ident])]
    (load-asset-from-pool db asset-pool asset-path)))


(defn load-asset-if-missing [db asset-pool-ident asset-path]
  (if (asset db asset-pool-ident asset-path)
    db
    (load-asset db asset-pool-ident asset-path)))


(defn- load-startup-assets-from-pool
  [db asset-pool]
  (reduce
   (fn [db asset-path]
     (load-asset-from-pool db asset-pool asset-path))
   db
   (-> asset-pool :asset-pool/load-on-startup)))


(defn- q-asset-pools-with-load-on-startup []
  '[:find ?e
    :where
    [?e :asset-pool/load-on-startup _]])


(defn load-startup-assets [app-db]
  (reduce
   (fn [app-db [asset-pool-id]]
     (load-startup-assets-from-pool app-db (am/entity! asset-pool-id)))
   app-db
   (am/q! (q-asset-pools-with-load-on-startup))))


(defn reg-sub-for-asset-pool [asset-pool]
  (let [asset-pool-ident (-> asset-pool :asset-pool/ident)]
    (rf/reg-sub
     asset-pool-ident
     (fn [db [_ asset-path]]
       (asset db asset-pool-ident asset-path)))))


