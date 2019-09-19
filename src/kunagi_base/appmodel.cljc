(ns kunagi-base.appmodel
  (:require
   [clojure.spec.alpha :as s]
   [datascript.core :as d]

   [kunagi-base.logging.tap]
   [kunagi-base.utils :as utils]))


;;FIXME update entities instead of inserting


(defn- new-db []
  (let [schema {:module/id           {:db/unique :db.unique/identity}
                :module/ident        {:db/unique :db.unique/identity}
                :module/namespace    {:db/unique :db.unique/identity}
                :entity-model/id     {:db/unique :db.unique/identity}
                :entity-model/ident  {:db/unique :db.unique/identity}
                :entity-model/module {:db/type :db.type/ref}}]
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
    ;; (tap> [:!!! ::new-schema schema])
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


(defn- complete-entity-by-model [entity]
  (let [type (-> entity :entity/type)
        model (entity! [:entity-model/ident type])]
    ;;(tap> [:!!! ::complete type model])
    ;; TODO verify ident-attr-spec
    (if-not model
      (do
        (tap> [:wrn ::missing-model-for-entity type])
        entity)
      (let [attr-models (-> model :entity-model/attrs)]
        (doseq [k (-> entity keys)]
          (if-not (or (= :entity/type k)
                   (#{:id :module} (keyword (name k))))
            (if-let [attr-model (-> attr-models (get k))]
              (if-let [spec (-> attr-model :spec)]
                (utils/assert-spec
                 spec (-> entity k)
                 (str "Invalid attribute "
                      (pr-str k) " = " (pr-str (-> entity k))
                      " in entity definition "
                      (pr-str (-> entity (get (keyword (name type) "id"))))
                      ".")))
              (tap> [:wrn ::missing-attr-in-entity-model k]))))
        entity))))


(s/def ::entity-id qualified-keyword?)
(s/def ::entity-model-ident simple-keyword?)
(s/def ::module-ident simple-keyword?)
(s/def ::identity? boolean?)
(s/def ::ref? boolean?)
(s/def ::req? boolean?)
(s/def ::cardinality-many? boolean?)
(s/def ::unique-identity? boolean?)
(s/def ::entity-attr-model (s/keys :opt-un [::unique-identity? ::ref? ::req? ::cardinality-many?]))
(s/def ::entity-model-attrs (s/map-of ::entity-attr-k ::entity-attr-model))


(defn- extend-schema-for-ks [schema property value ks]
  (reduce
   (fn [schema k]
     (assoc-in schema [k property] value))
   schema ks))

(defn def-entity-model [module-ident entity-model-id attrs-models]
  (utils/assert-spec
   ::module-ident module-ident
   (str "Invalid entity-model module-ident " module-ident "."))
  (utils/assert-spec
   ::entity-id entity-model-id
   (str "Invalid entity-model entity-model-id " entity-model-id "."))
  (utils/assert-spec
   ::entity-model-attrs attrs-models
   (str "Invalid entity-model attrs " attrs-models "."))
  (tap> [:dbg ::def-entity-model entity-model-id])

  (let [type (keyword (name entity-model-id))
        entity-model {:entity-model/id entity-model-id
                      :entity-model/ident type
                      :entity-model/module [:module/ident module-ident]
                      :entity-model/attrs attrs-models}

        schema {(keyword (name type) "module") {:db/type :db.type/ref}
                (keyword (name type) "id") {:db/unique :db.unique/identity}}
        ks (keys attrs-models)
        schema (extend-schema-for-ks
                schema :db/type :db.type/ref
                (->> ks (filter #(get-in attrs-models [% :ref?]))))
        schema (extend-schema-for-ks
                schema :db/cardinality :db.cardinality/many
                (->> ks (filter #(get-in attrs-models [% :cardinality-many?]))))
        schema (extend-schema-for-ks
                schema :db/unique :db.unique/identity
                (->> ks (filter #(get-in attrs-models [% :unique-identity?]))))]
    ;;(tap> [:!!! ::schema-ext schema])
    (swap! !db extend-schema schema)

    (update-facts
     [entity-model])))


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
    {:req {(keyword (name type) "id") ::entity-id
           (keyword (name type) "module") ::entity-ref}}
    (str "Invalid entity: " (pr-str entity) "."))
   (let [db @!db
         entity (assoc entity :entity/type type)
         entity (complete-entity-by-model entity)]
     (update-facts
      [entity]))))


(s/def ::module-id qualified-keyword?)

(defn def-module
  [module]
  (let [module-id (get module :module/id)
        _ (utils/assert-spec ::module-id
                             module-id
                             "Invalid :module/id passed to def-module.")
        module-ident (keyword (name module-id))
        module-namespace (namespace module-id)
        other-module-id (first
                         (q!
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


