(ns kunagi-base.appmodel
  (:require
   [datascript.core :as d]

   [kunagi-base.logging.tap]
   [kunagi-base.utils :as utils]))


;;FIXME update entities instead of inserting


(defn- new-db []
  (let [schema {:index/modules {:db/type :db.type/ref
                                :db/cardinality :db.cardinality/many}
                :index/current-module {:db/type :db.type/ref}}
        index {:entity/type :index}]
    (-> (d/empty-db schema)
        (d/db-with [index]))))


(defn q
  ([db query]
   (q db query []))
  ([db query params]
   (try
     (let [ret (apply d/q (into [query db] params))]
       ;; (tap> [:!!! ::query-result {:query query
       ;;                             :result ret}])
       ret)
     (catch  #?(:clj Exception :cljs :default) ex
       (tap> [:err ::query-failed ex])
       (throw (ex-info "Query failed"
                       {:query query}
                       ex))))))


(defn entity
  [db id]
  (d/entity db id))


(defn current-module-id [db]
  (first
   (q db
      '[:find [?module-id]
        :where
        [?e :index/current-module ?module-id]])))


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
  (let [datoms (d/datoms db :eavt)
        schema (merge (get db :schema)
                      schema-extension)]
    (d/init-db datoms schema)))


(defn def-extension [{:as extension
                      :keys [schema]}]
  (tap> [:dbg ::def-extension extension])
  (swap! !db extend-schema schema))


(defn update-facts [facts]
  (try
    (swap! !db d/db-with facts)
    (catch #?(:clj Exception :cljs :default) ex
      (tap> [:err ::update-failed ex])
      (throw (ex-info "Update failed"
                      {:facts facts}
                      ex)))))


(defn register-entity
  [type entity]
  ;; TODO extract type from :???/ident
  (tap> [:dbg ::register entity])
  (let [db @!db
        entity-id -1
        module-id (current-module-id db)
        entity (assoc entity :db/id entity-id)
        entity (assoc entity :entity/type type)
        entity (if (= :module type)
                 entity
                 (assoc entity (keyword (name type) "module") module-id))]
    (update-facts
     [entity])))


(defn def-module
  [module]
  (tap> [:dbg ::def-module module])
  (let [db @!db
        module (assoc module :entity/type :module)
        module (assoc module :db/id -1)
        index-id (first
                  (q db '[:find [?e]
                          :where
                          [?e :entity/type :index]]))]
    (update-facts
     [[:db/add index-id :index/current-module -1]
      module])))
