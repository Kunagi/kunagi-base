(ns kunagi-base.dsdb.api
  (:require
   [clojure.spec.alpha :as s]
   [datascript.core :as d]
   [kunagi-base.utils :as utils]))


(defn new-db [schema]
  (let [db (-> (d/empty-db schema))]
    {:db db}))


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


(defn update-facts [db facts]
  ;; (tap> [:!!! ::update-facts facts])
  (try
    (update db :db d/db-with facts)
    (catch #?(:clj Exception :cljs :default) ex
      (tap> [:err ::update-failed ex])
      (throw (ex-info "Update failed"
                      {:facts facts}
                      ex)))))


