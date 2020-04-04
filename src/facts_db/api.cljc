(ns facts-db.api
  (:require
   [bindscript.api :refer [def-bindscript]]

   [facts-db.validating :as validating]
   [facts-db.reading :as reading]
   [facts-db.updating2 :as updating]
   [facts-db.ddapi :as ddapi]))

;;;

(defn new-uuid
  []
  (str #?(:cljs (random-uuid)
          :clj  (java.util.UUID/randomUUID))))


(def update-facts updating/update-facts)

(def merge-db updating/merge-db)

(defn query [db query]
  (ddapi/<query db query))

(def apply-events ddapi/events>)

(def def-api ddapi/def-api)

(def def-event ddapi/def-event)

(def def-query ddapi/def-query)


(defn new-db
  ([]
   (updating/new-db))
  ([api-ident]
   (ddapi/new-db api-ident {}))
  ([api-ident args]
   (ddapi/new-db api-ident args)))


(defn contains-entity?
  [db id]
  (reading/contains-entity? db id))


(defn fact
  [db entity-id fact-name]
  (reading/fact db entity-id fact-name))


(defn tree
  [db id refs]
  (reading/tree db id refs))


(defn tree-or-nil
  [db id refs]
  (if (reading/contains-entity? db id)
    (tree db id refs)))


(defn trees
  [db ids refs]
  (reading/trees db ids refs))


(defn find-ids
  [db predicate]
  (reading/find-ids db predicate))


(defn find-id
  [db predicate]
  (reading/find-id db predicate))


(defn id-by-kv
  [db k v]
  (find-id db #(= v (get % k))))


;; (defn pull-one
;;   [db id-or-predicate pull-template]
;;   (when id-or-predicate
;;     (if (fn? id-or-predicate)
;;       (pull-one (find-id db id-or-predicate))
;;       (reading/tree db id-or-predicate pull-template))))
