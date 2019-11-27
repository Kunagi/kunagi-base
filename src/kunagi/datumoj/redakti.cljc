(ns kunagi.datumoj.redakti
  (:require
   [kunagi.redakti.buffer :as buffer]

   [kunagi.datumoj.schema.entity :as entity]))


(defn- schema-entity->root-buffer-node [entity]
  (let [singleton? (-> entity :singleton?)
        label ((if singleton? entity/label-1 entity/label-n ) entity)]
    {:redakti.node/type :leaf
     :redakti.node/text label}))


(defn schema->root-buffer [schema]
  (let [entities (-> schema :entities)
        tree {:redakti.node/type  :column
              :redakti.node/nodes (map schema-entity->root-buffer-node entities)}]
    (buffer/new-buffer tree)))
