(ns kunagi-base.assets
  (:require
   [re-frame.core :as rf]
   [facts-db.api :as db]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.appmodel :as appmodel]
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


(defn asset-for-output [path context]
  (let [[module-ident asset-pool-ident asset-path] path
        [req-perms] (appmodel/q!
                     '[:find [?req-perms]
                       :in $ ?module-ident ?asset-pool-ident
                       :where
                       [?a :asset-pool/req-perms ?req-perms]
                       [?a :asset-pool/ident ?asset-pool-ident]
                       [?a :asset-pool/module ?m]
                       [?m :module/ident ?module-ident]]
                     [module-ident asset-pool-ident])]
    (if-not (auth/context-has-permissions? context req-perms)
      :auth/not-permitted
      (get-in context [:db :assets module-ident asset-pool-ident asset-path]))))



(defn- load-asset
  [asset-pool app-db asset-path]
  (let [module-ident (-> asset-pool :asset-pool/module :module/ident)
        asset-pool-ident (:asset-pool/ident asset-pool)
        load-f (or (:asset-pool/load-f asset-pool)
                   loader/default-load-f)
        db-path [:assets module-ident asset-pool-ident asset-path]]
    (tap> [:dbg ::load-asset {:db-path db-path
                              :asset-path asset-path}])
    (->> (load-f app-db asset-pool asset-path)
         (assoc-in app-db db-path))))


(defn- load-assets-from-pool
  [app-db [asset-pool-id]]
  (let [asset-pool (appmodel/entity! asset-pool-id)
        assets-paths (:asset-pool/load-on-startup asset-pool)]
    (reduce (partial load-asset asset-pool) app-db assets-paths)))


(defn load-startup-assets [app-db]
  (reduce load-assets-from-pool
          app-db
          (appmodel/q! '[:find ?e ?asset-paths
                         :where
                         [?e :asset-pool/load-on-startup? true]
                         [?e :asset-pool/load-on-startup ?asset-paths]])))
