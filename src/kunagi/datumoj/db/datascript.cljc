(ns kunagi.datumoj.db.datascript
  (:require
   [clojure.test :refer [deftest is]]
   [datascript.core :as d]

   [kunagi.datumoj.schema :as schema]
   [kunagi.datumoj.schema.entity.attr :as attr]
   [kunagi.datumoj.db :as db]))


(defrecord Db [schema datascript-db]
    db/Db
    (schema [this] schema))


(defn datumoj-schema->ds-schema [datumoj-schema]
  (reduce
   (fn [ds-schema entity]
     (reduce
      (fn [ds-schema attr]
        (let [attr-key (keyword
                                (str (-> datumoj-schema :ident name)
                                     "."
                                     (-> entity :ident name))
                                (-> attr :ident name))]
          (assoc ds-schema attr-key
                 (cond-> {:db/ident attr-key}
                   (-> attr :unique?) (assoc :db/unique :db.unique/identity)
                   (-> attr :ref?)  (assoc :db/valueType :db.type/ref)
                   (-> attr :ref-n?) (assoc :db/cardinality :db.cardinality/many)))))
      ds-schema
      (-> entity :attrs)))
   {}
   (-> datumoj-schema :entities)))


(deftest datumoj-schema->ds-schema-test
  (let [datumoj-schema (schema/new-schema
                        {:ident :test
                         :entities [{:ident :show
                                     :attrs [{:ident :artists
                                              :type :ref-1}
                                             {:ident :title
                                              :type :text-1
                                              :unique? true}]}]})
        ds-schema (datumoj-schema->ds-schema datumoj-schema)
        _ (is (= {:db/ident  :test.show/title
                  :db/unique :db.unique/identity}
                 (-> ds-schema :test.show/title)))
        _ (is (= {:db/ident :test.show/artists
                  :db/valueType :db.type/ref}
                 (-> ds-schema :test.show/artists)))]
    ds-schema))


(defn new-db
  ([datumoj-schema]
   (new-db datumoj-schema nil))
  ([datumoj-schema datoms]
   (let [ds-schema (datumoj-schema->ds-schema datumoj-schema)]
     (if datoms
       (d/init-db datoms ds-schema)
       (d/empty-db ds-schema)))))


(comment

  (def ds-schema {})

  (def datumoj-schema
    {:ident    :tv
     :entities [{:ident :show
                 :attrs [{:ident :title}]}]})

  (def entity (get-in datumoj-schema [:entities 0]))

  (let [schema {:ident    :tv
                :entities [{:ident :actor
                            :attrs [{:ident :name}]}
                           {:ident :show
                            :attrs [{:ident :title}]}]}]
    (datumoj-schema->ds-schema schema))
    ;;     db (new-db schema)]
    ;; db)


  (let [schema {}
        db (d/empty-db schema)
        db (d/db-with db [{:hello :world}])]
    db))
