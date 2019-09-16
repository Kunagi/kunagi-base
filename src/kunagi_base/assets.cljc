(ns kunagi-base.assets
  (:require
   [re-frame.core :as rf]
   [facts-db.api :as db]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.appmodel :as appmodel :refer [def-module]]
   [kunagi-base.events :refer [def-event def-event-handler]]
   [kunagi-base.assets.loader :as loader]))


(appmodel/def-extension
  {:schema {:asset-pool/module {:db/type :db.type/ref}}})


(defn def-asset-pool [asset-pool]
  (appmodel/register-entity
   :asset-pool
   asset-pool))


(rf/reg-sub
 :assets/asset
 (fn [db [_ path]]
   (-> db
       :assets
       (get-in path))))


(defn- load-asset
  [app-db asset-pool asset-path]
  (let [load-f (or (:asset-pool/load-f asset-pool)
                   loader/default-load-f)]
    (load-f app-db asset-pool asset-path)))


(defn- provide-asset-for-output [asset-path asset-pool context]
  (let [module-ident (-> asset-pool :asset-pool/module :module/ident)
        asset-pool-ident (-> asset-pool :asset-pool/ident)
        asset (get-in context [:db :assets module-ident asset-pool-ident asset-path])]
    (if asset
      asset
      (load-asset (-> context :db) asset-pool asset-path))))


(defn- asset-pool-id [[module-ident asset-pool-ident]]
  (first
   (appmodel/q!
    '[:find [?a]
      :in $ ?module-ident ?asset-pool-ident
      :where
      [?a :asset-pool/ident ?asset-pool-ident]
      [?a :asset-pool/module ?m]
      [?m :module/ident ?module-ident]]
    [module-ident asset-pool-ident])))


(defn asset-for-output [path context]
  (let [[module-ident asset-pool-ident asset-path] path
        asset-pool-id (asset-pool-id path)
        asset-pool (appmodel/entity! asset-pool-id)
        req-perms (-> asset-pool :asset-pool/req-perms)]
    (if-not (auth/context-has-permissions? context req-perms)
      :auth/not-permitted
      (provide-asset-for-output asset-path asset-pool context))))




(defn- load-asset-and-store
  [asset-pool app-db asset-path]
  (let [module-ident (-> asset-pool :asset-pool/module :module/ident)
        asset-pool-ident (:asset-pool/ident asset-pool)
        db-path [:assets module-ident asset-pool-ident asset-path]]
    (tap> [:dbg ::load-asset {:db-path db-path
                              :asset-path asset-path}])
    (assoc-in app-db db-path (load-asset app-db asset-pool asset-path))))


(defn- load-assets-from-pool
  [app-db [asset-pool-id]]
  (let [asset-pool (appmodel/entity! asset-pool-id)
        assets-paths (:asset-pool/load-on-startup asset-pool)]
    (reduce (partial load-asset-and-store asset-pool) app-db assets-paths)))


(defn load-startup-assets [app-db]
  (reduce load-assets-from-pool
          app-db
          (appmodel/q! '[:find ?e ?asset-paths
                         :where
                         [?e :asset-pool/load-on-startup ?asset-paths]])))


(defn on-asset-requested [[_ path] context]
  (tap> [:dbg ::asset-requested path])
  (let [asset (asset-for-output path context)
        response-f (-> context :comm/response-f)]
    (response-f [:assets/asset-received {:path path
                                         :data asset}])))


(def-module
  {:module/id ::assets})


(def-event
  {:event/id ::asset-requested
   :event/ident :assets/asset-requested
   :event/req-perms [:assets/read]})


(def-event-handler
  {:event-handler/id ::asset-requested
   :event-handler/event-ident :assets/asset-requested
   :event-handler/f on-asset-requested})
