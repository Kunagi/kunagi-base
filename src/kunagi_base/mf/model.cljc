(ns kunagi-base.mf.model
  (:require
   [clojure.spec.alpha :as s]
   [datascript.core :as d]
   [kunagi-base.utils :as utils]))


(defn new-model []
  (let [schema {:module/name {:db/unique :db.unique/identity}
                :cursor/target {:db/type :db.type/ref}}]
                                        ;:db/unique :db.unique/identity}}]
    (-> (d/empty-db schema))))


;; helpers

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


(defn q-ids [db wheres & args]
  (let [query '[:find ?e
                :in $ ?1 ?2 ?3
                :where]
        query (into query wheres)]
    (map first (q db query args))))


(defn q-id [db wheres]
  (let [query '[:find [?e]
                :where]
        query (into query wheres)]
    (first (q db query))))


(defn entity [db id]
  (d/entity db id))


(defn entities [db ids]
  (map #(d/entity db %) ids))


(defn update-facts [db facts]
  ;; (tap> [:!!! ::update-facts facts])
  (try
    (d/db-with db facts)
    (catch #?(:clj Exception :cljs :default) ex
      (tap> [:err ::update-failed ex])
      (throw (ex-info "Update failed"
                      {:facts facts}
                      ex)))))

;;;

(defn ids [model]
  (q-ids model
         '[[?e _ _]]))
