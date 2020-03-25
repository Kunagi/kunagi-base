(ns kcu.app-model
  (:require
   [clojure.spec.alpha :as s]
   [kcu.utils :as u]))


(s/def ::type-id qualified-keyword?)
(s/def ::model-id qualified-keyword?)


(defn new-appmodel []
  {:types {}
   :models {}
   :idx {}})


(defn type [this type-id]
  (if-let [type (get-in this [:types type-id])]
    type
    (throw (ex-info (str "Model-Type `" type-id "` does not exist. "
                         "Types: `" (-> this :types keys) "`.")
                    {:type-id type-id}))))


(defn add-type [this type]
  (let [type-id (u/getm type :id ::type-id)]
    (-> this
        (assoc-in [:types type-id] type))))


(defn validate-model-attr [attr-type model]
  (let [k (-> attr-type :k)
        v (get model k)]
    (if-not v
      (when (-> attr-type :mandatory?)
        (throw (ex-info (str "Missing mandatory attr `" k "`.")
                        {:attr-type attr-type
                         :model model})))
      (when-let [spec (-> attr-type :spec)]
       (when-not (s/valid? spec v)
         (throw (ex-info (str "Invalid value for attr `" k "`. "
                              (s/explain-str spec v))
                         {:model model
                          :attr-type attr-type
                          :v v
                          :k k})))))))


(defn- validate-model-attrs [type model]
  (doseq [attr (-> type :attrs)]
    (validate-model-attr attr model)))


(defn- update-indexes [this type model]
  (let [model-id (get model :id)
        index-keys (reduce (fn [index-keys attr]
                             (if (-> attr :index?)
                               (conj index-keys (-> attr :k))
                               index-keys))
                           '(:type)
                           (-> type :attrs))]
    (reduce (fn [this k]
              (update-in this
                         [:idx (-> type :id) k (get model k)]
                         #(conj (or % #{}) model-id)))
            this
            index-keys)))


(defn add-model [this model]
  (let [model-id (u/getm model :id ::model-id)
        type-id (u/getm model :type ::type-id)
        type (type this type-id)]
      (validate-model-attrs type model)
      (-> this
          (assoc-in [:models model-id] model)
          (update-indexes type model))))


(defn models-by-index [this type-id k v]
  (get-in this [:idx type-id k v]))
