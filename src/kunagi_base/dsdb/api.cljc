(ns kunagi-base.dsdb.api
  (:require
   [clojure.spec.alpha :as s]
   [datascript.core :as d]
   [kunagi-base.utils :as utils]))


;;; db type definition


(s/def ::db-type-identifier (= ::db-type-identifier))
(s/def ::event-ident keyword?)
(s/def ::event-handler fn?)
(s/def ::attr-ident qualified-keyword?)

(s/def ::attr-flag (s/or :ref #(= :ref %)
                         :many #(= :many %)
                         :uid #(= :uid %)))
(s/def ::attr-flags (s/coll-of ::attr-flag))


(defn new-type []
  (atom {::db-type-identifier ::db-type-identifier}))


(defn attr-flags->schema [attr-spec]
  (reduce
   (fn [schema spec-element]
     (case spec-element
       :ref (assoc schema :db/type :db.type/ref)
       :many (assoc schema :db/cardinality :db.cardinality/many)
       :uid (assoc schema :db/unique :db.unique/identity)
       (throw (ex-info (str "Unsupported attr-spec-element: " spec-element)
                       {:unsupported-element spec-element
                        :attr-spec attr-spec}))))
   {}
   attr-spec))


(defn def-attr [db-type attr-ident attr-flags]
  (utils/assert-spec ::attr-ident attr-ident ::def-attr)
  (utils/assert-spec ::attr-flags attr-flags ::def-attr)
  (let [entity-name-key (keyword (namespace attr-ident))
        attr-name-key (keyword (name attr-ident))
        attr-schema (attr-flags->schema attr-flags)
        attr-spec {:flags (into #{} attr-flags)}]
    (swap! db-type
           (fn [db-type]
             (-> db-type
                 (assoc-in [:entities entity-name-key :attrs attr-name-key] attr-spec)
                 (assoc-in [:schema attr-ident] attr-schema))))))


(defn def-event [db-type event-ident event-handler]
  (utils/assert-spec ::event-ident event-ident ::def-event)
  (utils/assert-spec ::event-handler event-handler ::def-event)
  (swap! db-type assoc-in [:events event-ident :handler] event-handler))


;;; new-db


(defn new-db [db-type]
  (let [schema (if (map? db-type)
                 db-type
                 (-> @db-type :schema))
        db (-> (d/empty-db schema))]
    {:db-type db-type
     :db db}))


;; helpers

(defn db [db]
  (-> db :db))


(defn datoms [db]
  (d/datoms (-> db :db) :eavt))


(defn extend-schema [db schema-extension]
  (let [datoms (or (datoms db) [])
        schema (merge
                (get-in db [:db :schema])
                schema-extension)]
    ;; (tap> [:!!! ::new-schema schema])
    (assoc db :db (d/init-db datoms schema))))


(defn q
  ([db query]
   (q db query []))
  ([db query params]
   (try
     (let [ret (apply d/q (into [query (-> db :db)]
                                params))]
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


(defn q-ids [db wheres & args]
  (let [query '[:find ?e
                :in $ ?1 ?2 ?3
                :where]
        query (into query wheres)]
    (map first (q db query args))))


(defn q-id [db wheres]
  (let [query '[:find [?e]
                :in $ ?1 ?2 ?3
                :where]
        query (into query wheres)]
    (first (q db query))))


(defn entity [db id]
  (d/entity (-> db :db) id))


(defn entities [db ids]
  (let [db (-> db :db)]
    (map #(d/entity db %) ids)))


(defn pull [db pattern id]
  (let [db (-> db :db)]
    (d/pull db pattern id)))


(defn pull-many [db pattern ids]
  (let [db (-> db :db)]
    (d/pull-many db pattern ids)))


(defn pull-query [db pattern wheres & args]
  (let [query '[:find ?e
                :in $ ?1 ?2 ?3
                :where]
        query (into query wheres)]
    (pull-many db pattern (map first (q db query args)))))


(defn update-facts [db facts]
  ;; (tap> [:!!! ::update-facts facts])
  (try
    (update db :db d/db-with facts)
    (catch #?(:clj Exception :cljs :default) ex
      (tap> [:err ::update-failed ex])
      (throw (ex-info "Update failed"
                      {:facts facts}
                      ex)))))


;;; applying events


(defn event-handler [db event-ident]
  (let [db-type (get db :db-type)
        _ (when-not db-type (throw (ex-info "Db has no :db-type"
                                            {:db db})))]
    (-> @db-type
        :events
        (get event-ident)
        :handler)))


(defn apply-event [db event]
  (let [[event-ident event-args] event
        handler (event-handler db event-ident)
        _ (when-not handler (throw (ex-info (str "Missing event handler: " event-ident)
                                            {:event-ident event-ident
                                             :available-handlers
                                             (-> db :db-type deref :events keys)})))
        facts (handler db event-args)]
    (update-facts db facts)))


(defn apply-events [db events]
  (reduce apply-event db events))
