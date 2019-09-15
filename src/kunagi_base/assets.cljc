(ns kunagi-base.assets
  (:require
   [facts-db.api :as db]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.appmodel :as appmodel]))


(defn asset-from-db [db module-ident asset-pool-ident asset-path]
  (get-in db [:asset-pools module-ident asset-pool-ident asset-path]))


(defn asset-for-output [path context]
  (let [[module-ident asset-pool-ident asset-path] path
        model (appmodel/model)
        asset-pool-id (appmodel/entity-id module-ident :asset-pool asset-pool-ident)
        asset-pool (db/tree model asset-pool-id {})
        _ (tap> [:!!! ::asset-pool asset-pool-id asset-pool])
        req-perms (:asset-pool/req-perms asset-pool)]
    (if-not (auth/context-has-permissions? context req-perms)
      :auth/not-permitted
      (-> context
          :db
          (asset-from-db module-ident asset-pool-ident asset-path)))))



(defn asset-pools-with-load-on-startup [pull-template]
  (->> (appmodel/entities (appmodel/model) :index/asset-pools pull-template)
       (filter #(get % :asset-pool/load-on-startup?))))


(defn def-asset-pool [asset-pool]
  (appmodel/register-entity
   :asset-pool
   asset-pool))


(defn- load-asset [asset-pool app-db asset-path]
  (let [load-f           (-> asset-pool :asset-pool/load-f)
        module-ident     (-> asset-pool :asset-pool/module :module/ident)
        asset-pool-ident (-> asset-pool :asset-pool/ident)
        db-path [:asset-pools module-ident asset-pool-ident asset-path]]
    (tap> [:dbg ::load-asset {:db-path db-path
                              :asset-path asset-path}])
    (->> (load-f app-db asset-path)
         (assoc-in app-db db-path))))


(defn- load-assets-from-pool [app-db asset-pool]
  (let [module-ident (-> asset-pool :asset-pool/module :module/ident)
        assets-paths (-> asset-pool :asset-pool/load-on-startup)]
    (reduce (partial load-asset asset-pool) app-db assets-paths)))


(defn load-assets [app-db]
  (reduce load-assets-from-pool
          app-db
          (asset-pools-with-load-on-startup {:asset-pool/module {}})))
