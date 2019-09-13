(ns kunagi-base.resources
  (:require
   [kunagi-base.appmodel :as appmodel]))


(defn resources-with-load-on-startup [pull-template]
  (->> (appmodel/entities :index/resources pull-template)
       (filter #(get % :resource/load-on-startup?))))


(defn def-resource [resource]
  (appmodel/register-entity
   :resource
   resource))


(defn- load-resource [app-db resource]
  (let [module-ident (-> resource :resource/module :module/ident)
        resource-ident (-> resource :resource/ident)
        path [:resources module-ident resource-ident]]
    (tap> [:dbg ::load-resource path resource])
    (->> ((:resource/load-f resource) app-db)
         (assoc-in app-db path))))


(defn load-resources [app-db]
  (reduce load-resource
          app-db
          (resources-with-load-on-startup {:resource/module {}})))
