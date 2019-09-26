(ns kunagi-base.modules.assets.api
  (:require
   [re-frame.core :as rf]
   [facts-db.api :as db]
   [kunagi-base.utils :as utils]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.appmodel :as am :refer [def-module]]
   [kunagi-base.modules.assets.loader :as loader]))


;; TODO deprecated: user should be unaware of source
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
   (am/q!
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
        asset-pool (am/entity! asset-pool-id)
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
  (let [asset-pool (am/entity! asset-pool-id)
        assets-paths (:asset-pool/load-on-startup asset-pool)]
    (reduce (partial load-asset-and-store asset-pool) app-db assets-paths)))


(defn load-startup-assets [app-db]
  (reduce load-assets-from-pool
          app-db
          (am/q! '[:find ?e ?asset-paths
                   :where
                   [?e :asset-pool/load-on-startup ?asset-paths]])))


(defn on-asset-requested [[_ path] context]
  (tap> [:dbg ::asset-requested path])
  (let [asset (asset-for-output path context)
        response-f (-> context :comm/response-f)]
    (response-f [:assets/asset-received {:path path
                                         :data asset}])))


(defn load-asset! [db path]
  (let [[module-ident asset-pool-ident asset-path] path
        asset-pool-id (asset-pool-id path)
        asset-pool (am/entity! asset-pool-id)]
    (load-asset db asset-pool asset-path)
    db))
