(ns kunagi.datumoj.redakti-root-buffer
  (:require
   [kunagi.redakti :as redakti]
   [kunagi.redakti.buffer :as buffer]

   [kunagi.datumoj.db :as db]
   [kunagi.datumoj.schema.entity :as entity]))


(defn- node-for-entity [entity]
  (let [singleton? (-> entity :singleton?)
        label ((if singleton? entity/label-1 entity/label-n ) entity)]
    {:redakti.node/type :leaf
     :redakti.node/text label
     :redakti.node/payload [:entity (-> entity :ident)]}))


(defn !enter [db redakti]
  (tap> [:!!! ::!enter])
  (let [payload (redakti/node-payload redakti)]
    (case (first payload)
      :entity (redakti/!goto-sub-buffer redakti (buffer/new-buffer {:redakti.node/type :leaf
                                                                    :redakti.node/text (str payload)}))
      nil)))


(defn db->root-buffer [db]
  (let [schema (db/schema db)
        entities (-> schema :entities)
        tree {:redakti.node/type  :column
              :redakti.node/text (-> schema :ident name)
              :redakti.node/nodes
              [{:redakti.node/type :column ;; TODO inline
                :redakti.node/text "Entities"
                :redakti.node/nodes (map node-for-entity entities)}]}]
    (-> (buffer/new-buffer tree)
        (buffer/reg-action
         {:ident :enter
          :f     #(!enter db %)}))))
