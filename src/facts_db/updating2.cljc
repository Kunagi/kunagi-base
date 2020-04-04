(ns facts-db.updating2
  (:require
   [clojure.spec.alpha :as s]
   [bindscript.api :refer [def-bindscript]]
   [conform.api :as conform]
   [clojure.set :as set]
   [facts-db.validating]))

(s/def ::fact-key keyword?)

(s/def ::cr-operation-key #{:db/add-1 :db/add-n :db/rem-1 :db/rem-n})
(s/def ::cr-operation (s/or :simple-key ::fact-key
                            :coperation (s/cat :op ::cr-operation-key :fact ::fact-key)))
(s/def ::cr-value any?)
(s/def ::cr-entity (s/map-of ::cr-operation ::cr-value))


(defn new-db
  []
  {:db/config {:db/id :db/config}})


(defn new-uuid
  []
  (str #?(:cljs (random-uuid)
          :clj  (java.util.UUID/randomUUID))))


(defn collection? [value]
  (or (vector? value)
      (set? value)
      (list? value)))


(defn conform-cr-to-collection
  [cr]
  (if (collection? cr) cr (list cr)))


(defn validate-cr-entity
  [cr-entity]
  (conform/validate  ::update-facts
                    [:val cr-entity ::cr-entity])
  cr-entity)


(defn assoc-id [cr-entity]
  (if (:db/id cr-entity) cr-entity (assoc cr-entity :db/id (new-uuid))))


(defn update-entity-fact-by-operation
  [entity [op-key fact-key] v]
  (cond

    (= :db/add-1 op-key)
    (update entity fact-key #(if % (conj % v) #{v}))

    (= :db/add-n op-key)
    (update entity fact-key #(into (or % #{}) v))

    (= :db/rem-1 op-key)
    (update entity fact-key #(if % (disj % v) #{}))

    (= :db/rem-n op-key)
    (update entity fact-key #(set/difference (or % #{}) v))


    :else
    (throw (ex-info (str "Unsupported operation: " op-key) {}))))


(defn update-entity-fact
  [entity [k v]]
  (cond

    (vector? k)
    (update-entity-fact-by-operation entity k v)

    (nil? v)
    (dissoc entity k)

    :else
    (assoc entity k v)))


(defn update-entity-facts
  [entity facts]
  (reduce update-entity-fact entity facts))


(defn apply-cr-entity
  [db cre]
  (let [id     (:db/id cre)
        entity (or (get db id) {})]
    (assoc db id (update-entity-facts entity cre))))

(defn apply-cr
  [db cr]
  (reduce apply-cr-entity db cr))


(defn extract-refs-from-cr-entity
  [cr-entity]
  (reduce
   (fn [[entity referencing-entities] [k v]]
     (cond
       (= :db/add-ref-1 k)
       [(dissoc entity k) (conj referencing-entities
                                {:db/id                 (first v)
                                 (second v) (:db/id cr-entity)})]

       (= :db/rem-ref-1 k)
       [(dissoc entity k) (conj referencing-entities
                                {:db/id                 (first v)
                                 (second v) nil})]

       (= :db/add-ref-n k)
       [(dissoc entity k) (conj referencing-entities
                                {:db/id                 (first v)
                                 [:db/add-1 (second v)] (:db/id cr-entity)})]

       (= :db/rem-ref-n k)
       [(dissoc entity k) (conj referencing-entities
                                {:db/id                 (first v)
                                 [:db/rem-1 (second v)] (:db/id cr-entity)})]

       :else
       [entity referencing-entities]))
   [cr-entity '()]
   cr-entity))


(defn extract-refs-from-cr
  [cr]
  (reduce
   (fn [cr cr-entity]
     (let [[cr-entity referencing-entities] (extract-refs-from-cr-entity cr-entity)]
       (-> cr
           (conj cr-entity)
           (into referencing-entities))))
   '()
   cr))

(defn update-facts
  "Update one or multiple entities.
  Only provided facts are updated. Existing facts stay unchanged."
  [db change-request]
  (conform/validate ::update-facts
                    [:val db :db/db])
  (->> change-request
       (conform-cr-to-collection)
       (map validate-cr-entity)
       (map assoc-id)
       (extract-refs-from-cr)
       (apply-cr db)))


(def-bindscript ::update-facts
  db (new-db)

  ;; create a new entity with given id
  db (update-facts db {:db/id 1
                       :name "Witek"})

  ;; create a new entity with auto id
  db (update-facts db {:name "Hogi"})

  ;; update fact of existing entity
  db (update-facts db {:db/id 1
                       :name "Witoslaw"})

  ;; update facts fo multiple entities
  db (update-facts db [{:db/id 1
                        :name "Witek"}
                       {:db/id 2
                        :name "Kace"}])

  ;; remove fact from entity
  db (update-facts db {:db/id 1
                       :name nil})

  db (update-facts db {:db/id 1
                       :colors #{:red}})

  ;; add item to a set-fact
  db (update-facts db {:db/id 1
                       [:db/add-1 :colors] :blue})

  ;; add multiple items to a set-fact
  db (update-facts db {:db/id 1
                       [:db/add-n :colors] [:yellow :green]})

  ;; remove item from a set-fact
  db (update-facts db {:db/id 1
                       [:db/rem-1 :colors] :blue})

  ;; remove multiple items from a set-fact
  db (update-facts db {:db/id 1
                       [:db/rem-n :colors] [:yellow :green]})

  ;; create new entity and reference it
  db (update-facts db {:db/id 1
                       :friends #{}
                       :best-friend nil})
  db (update-facts db {:db/id 3
                       :db/add-ref-n [1 :friends]})
  db (update-facts db {:db/id 3
                       :db/add-ref-1 [1 :best-friend]})

  ;; remove references
  db (update-facts db {:db/id 3
                       :db/rem-ref-n [1 :friends]})
  db (update-facts db {:db/id 3
                       :db/rem-ref-1 [1 :best-friend]})

  ;; delete entity
  db (update-facts db {:db/id 3
                       :db/delete true}))

  ;;db (update-facts db :x))



(defn merge-db
  "Merge facts from `db2` into `db`."
  [db db2]
  (update-facts db (vals db2)))
