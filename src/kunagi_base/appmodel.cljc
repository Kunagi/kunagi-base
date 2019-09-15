(ns kunagi-base.appmodel
  (:require
   [datascript.core :as d]
   [kunagi-base.utils :as utils]))


(defn- new-db []
  (let [schema {:index/modules {:db/type :db.type/ref
                                :db/cardinality :db.cardinality/many}
                :index/current-module {:db/type :db.type/ref}}
        index {:entity/type :index}]
    (-> (d/empty-db schema)
        (d/db-with [index]))))

  ;; (-> (db/new-db)
  ;;     (db/update-facts
  ;;      [{:db/id index-id
  ;;        :index/modules []
  ;;        :index/current-module nil}])))




;; (defn entities [db index-ref pull-template]
;;   (-> db
;;       (db/tree index-id {index-ref pull-template})
;;       (get index-ref)))



;; (defn entity-id [module-ident entity-type entity-ident]
;;   (str entity-type ":"
;;        (when module-ident (str module-ident ":"))
;;        entity-ident))


;; (defn- assoc-id [entity module-ident entity-type entity-ident]
;;   (if (:db/id entity)
;;     entity
;;     (assoc entity :db/id (entity-id module-ident entity-type entity-ident))))

(defn q
  ([db query]
   (q db query []))
  ([db query params]
   (try
     (let [ret (apply d/q (into [query db] params))]
       (tap> [:!!! ::query-result {:query query
                                   :result ret}])
       ret)
     (catch  #?(:clj Exception :cljs :default) ex
       (tap> [:err ::query-failed ex])
       (throw (ex-info "Query failed"
                       {:query query}
                       ex))))))



(defn current-module-id [db]
  (first
   (q db
      '[:find [?module-id]
        :where
        [?e :index/current-module ?module-id]])))


;; (defn module-by-ident [db module-ident pull-template]
;;   (let [module-id (db/find-id db #(= module-ident (:module/ident %)))]
;;     (db/tree db module-id pull-template)))


;;;

(defonce !db (atom (new-db)))


(defn model-db []
  @!db)


(defn q!
  ([query params]
   (q @!db query params))
  ([query]
   (q @!db query)))


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
  (let [db @!db
        entity-id -1
        module-id (current-module-id db)
        ;;module-ident (db/fact db module-id :module/ident)
        ;;entity-ident (get entity (keyword (name type) "ident"))]
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
