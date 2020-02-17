(ns kunagi-base.appmodel
  (:require
   [clojure.spec.alpha :as s]

   [kunagi-base.logging.tap]
   [kunagi-base.utils :as utils]
   [kunagi-base.dsdb.api :as dsdb]
   [kunagi-base.context :as context]))


(s/def ::entity-ident simple-keyword?)
(s/def ::db-id int?)
(s/def ::entity-attr-k qualified-keyword?)
(s/def ::entity-lookup-ref (s/cat :k ::entity-attr-k
                                  :v (s/or :id ::entity-id
                                           :ident ::entity-ident)))
(s/def ::entity-ref (s/or :id ::db-id
                          :lookup-ref ::entity-lookup-ref))


;;FIXME update entities instead of inserting


(defn- new-db []
  (let [schema {:module/id           {:db/unique :db.unique/identity}
                :module/ident        {:db/unique :db.unique/identity}
                :module/namespace    {:db/unique :db.unique/identity}
                :entity-model/id     {:db/unique :db.unique/identity}
                :entity-model/ident  {:db/unique :db.unique/identity}
                :entity-model/module {:db/type :db.type/ref}}]
    (dsdb/new-db schema)))


(def q dsdb/q)

(def entity dsdb/entity)


;;;

(defonce !db (atom (new-db)))


(defn update-facts [facts]
  (swap! !db dsdb/update-facts facts))


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



(defn module-name-by-entity-id [entity-id]
  (-> (entity! [:module/namespace (namespace entity-id)])
      :module/ident
      name))


(s/def ::entity-id qualified-keyword?)
(s/def ::entity-model-ident simple-keyword?)
(s/def ::module-ident simple-keyword?)
(s/def ::identity? boolean?)
(s/def ::ref? boolean?)
(s/def ::req? boolean?)
(s/def ::cardinality-many? boolean?)
(s/def ::uid? boolean?)
(s/def ::entity-attr-model (s/keys :opt-un [::uid? ::ref? ::req? ::cardinality-many?]))
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
                (->> ks (filter #(get-in attrs-models [% :uid?]))))]
    ;;(tap> [:!!! ::schema-ext schema])
    (swap! !db dsdb/extend-schema schema)

    (update-facts
     [entity-model])))


(defn- entity-id [entity]
  (-> entity
      (get (keyword (name (-> entity :entity/type))
                    "id"))))


(defn- assert-attr-by-model [entity k attr-model]
  (when-not (or (= :entity/type k)
                (#{:id :module} (keyword (name k))))
    (if attr-model
      (if-let [spec (or (-> attr-model :spec)
                        (when (-> attr-model :ref?)
                          ::ref ::entity-lookup-ref))]
        (utils/assert-spec
         spec (-> entity k)
         (str "Invalid attribute "
              (pr-str k) " = " (pr-str (-> entity k))
              " in entity definition "
              (pr-str (entity-id entity))
              ".")))
      (tap> [:wrn ::missing-attr-in-entity-model {:missing-atr k
                                                  :attr-model attr-model
                                                  :provided-by-entity entity}]))))


(defn assert-req-attrs-exist [entity attr-models]
  (doseq [[k attr-model] attr-models]
    (when (or (-> attr-model :req?)
              (-> attr-model :uid?))
      (when-not (contains? entity k)
        (throw (ex-info (str "Missing attribute " (pr-str k)
                             " in entity definition "
                             (pr-str (entity-id entity)) ".")
                        {:entity entity}))))))


(defn- assert-by-entity-model [entity]
  (let [type (-> entity :entity/type)
        model (entity! [:entity-model/ident type])]
    ;;(tap> [:!!! ::complete type model])
    (when-not model
      (throw (ex-info (str "Missing entity-model " (pr-str type) ".")
                      {:entity entity})))
    (let [attr-models (-> model :entity-model/attrs)]
      (assert-req-attrs-exist entity attr-models)
      (doseq [k (-> entity keys)]
        (assert-attr-by-model entity k (-> attr-models (get k)))))))



(defn register-entity
  ([type entity]
   (register-entity type entity {}))
  ([type entity options]
   (tap> [:dbg ::register entity])
   (utils/assert-entity
    entity
    {:req {(keyword (name type) "id") ::entity-id}}
    (str "Invalid entity: " (pr-str entity) "."))
   (let [entity (assoc entity :entity/type type)

         id (get entity (keyword (name type) "id"))
         module-name (module-name-by-entity-id id)
         entity (assoc entity
                       (keyword (name type) "module") [:module/ident (keyword module-name)])]

         ;; _ (when-not (= [:module/ident (keyword module-name)]
         ;;                (get entity (keyword (name type) "module")))
         ;;     (throw (ex-info "Auuu" {:entity entity})))]

     (assert-by-entity-model entity)
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


