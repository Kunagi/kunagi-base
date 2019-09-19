(ns kunagi-base.appmodel
  (:require
   [clojure.spec.alpha :as s]
   [datascript.core :as d]

   [kunagi-base.logging.tap]
   [kunagi-base.utils :as utils]))


;;FIXME update entities instead of inserting


(defn- new-db []
  (let [schema {:module/id {:db/unique :db.unique/identity}
                :module/ident {:db/unique :db.unique/identity}
                :module/namespace {:db/unique :db.unique/identity}}]
    (-> (d/empty-db schema))))


(defn q
  ([db query]
   (q db query []))
  ([db query params]
   (try
     (let [ret (apply d/q (into [query db] params))]
       ;; (tap> [:!!! ::query-result {:query query
       ;;                             :result ret
       ;;                             :params params
       ;;                             :db db}])
       ret)
     (catch  #?(:clj Exception :cljs :default) ex
       (tap> [:err ::query-failed ex])
       (throw (ex-info "Query failed"
                       {:query query}
                       ex))))))


(defn entity
  [db id]
  (d/entity db id))


;;;

(defonce !db (atom (new-db)))


(defn model-db []
  @!db)


(defn q!
  ([query params]
   (q @!db query params))
  ([query]
   (q @!db query)))


(defn entity!
  [id]
  (entity @!db id))


(defn- extend-schema [db schema-extension]
  (let [datoms (or (d/datoms db :eavt) [])
        schema (merge (get db :schema)
                      schema-extension)]
    (d/init-db datoms schema)))


(defn def-extension [{:as extension
                      :keys [schema]}]
  (tap> [:dbg ::def-extension extension])
  (swap! !db extend-schema schema))


(defn update-facts [facts]
  ;; (tap> [:!!! ::update-facts facts])
  (try
    (swap! !db d/db-with facts)
    (catch #?(:clj Exception :cljs :default) ex
      (tap> [:err ::update-failed ex])
      (throw (ex-info "Update failed"
                      {:facts facts}
                      ex)))))


(s/def ::entity-id qualified-keyword?)
(s/def ::entity-ident simple-keyword?)
(s/def ::db-id int?)
(s/def ::entity-attr-k qualified-keyword?)
(s/def ::entity-lookup-ref (s/cat :k ::entity-attr-k
                                  :v (s/or :id ::entity-id
                                           :ident ::entity-ident)))
(s/def ::entity-ref (s/or :id ::db-id
                          :lookup-ref ::entity-lookup-ref))

(defn register-entity
  ([type entity]
   (register-entity type entity {}))
  ([type entity options]
   (tap> [:dbg ::register entity])
   (utils/assert-entity
    entity
    {:req {(keyword (name type) "module") ::entity-ref}}
    (str "Invalid entity: " (pr-str entity) "."))
   (let [db @!db
         id-key (keyword (name type) "id")
         entity-id (get entity id-key)
         _ (utils/assert-spec ::entity-id
                              entity-id
                              (str "Invalid " id-key " passed to register-entity."))
         entity (assoc entity :entity/type type)]
     (update-facts
      [entity]))))


(s/def ::module-id qualified-keyword?)

(defn def-module
  [module]
  (let [db @!db
        module-id (get module :module/id)
        _ (utils/assert-spec ::module-id
                             module-id
                             "Invalid :module/id passed to def-module.")
        module-ident (keyword (name module-id))
        module-namespace (namespace module-id)
        other-module-id (first
                         (q db
                            '[:find [?module-id]
                              :in $ ?module-namespace
                              :where
                              [?e :module/namespace ?module-namespace]
                              [?e :module/id ?module-id]]
                            [module-namespace]))
        _ (utils/assert (or (nil? other-module-id)
                            (= module-id other-module-id))
                        (str "Multiple modules in the same namespace: " module-namespace)
                        module-id other-module-id)
        module (assoc module :module/ident module-ident)
        module (assoc module :module/namespace module-namespace)
        module (assoc module :entity/type :module)]
    (tap> [:dbg ::def-module module])
    (update-facts [module])))
