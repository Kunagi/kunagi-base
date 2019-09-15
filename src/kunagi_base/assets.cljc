(ns kunagi-base.assets
  (:require
   [facts-db.api :as db]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.appmodel :as appmodel]))

(appmodel/def-extension
  {:schema {:asset/module {:db/type :db.type/ref}}})


(defn asset-for-output [path context]
  (let [[module-ident asset-pool-ident asset-path] path
        _ (tap> [:!!! ::asset-for-output path])
        [req-perms] (appmodel/q!
                     '[:find [?req-perms]
                       :in $ ?module-ident ?asset-pool-ident
                       :where
                       [?a :asset-pool/req-perms ?req-perms]
                       [?a :asset-pool/ident ?asset-pool-ident]
                       [?a :asset-pool/module ?m]
                       [?m :module/ident ?module-ident]]
                     [module-ident asset-pool-ident])
        _ (tap> [:!!! ::perms req-perms])]
    ;;TODO permissions
    (if-not (auth/context-has-permissions? context req-perms)
      :auth/not-permitted
      (get-in context [:db :assets module-ident asset-pool-ident asset-path]))))


(defn def-asset-pool [asset-pool]
  (appmodel/register-entity
   :asset-pool
   asset-pool))


(defn- load-asset
  [[module-ident asset-pool-ident load-f :as asset-pool] app-db asset-path]
  (let [db-path [:assets module-ident asset-pool-ident asset-path]]
    (tap> [:dbg ::load-asset {:db-path db-path
                              :asset-path asset-path}])
    (->> (load-f app-db asset-path)
         (assoc-in app-db db-path))))


(defn- load-assets-from-pool
  [app-db [_ _ _ assets-paths :as asset-pool]]
  (reduce (partial load-asset asset-pool) app-db assets-paths))


(defn load-startup-assets [app-db]
  (reduce load-assets-from-pool
          app-db
          (appmodel/q! '[:find ?module-ident ?asset-pool-ident ?load-f ?asset-paths
                         :where
                         [?e :asset-pool/load-on-startup? true]
                         [?e :asset-pool/ident ?asset-pool-ident]
                         [?e :asset-pool/load-f ?load-f]
                         [?e :asset-pool/load-on-startup ?asset-paths]
                         [?e :asset-pool/module ?m]
                         [?m :module/ident ?module-ident]])))
