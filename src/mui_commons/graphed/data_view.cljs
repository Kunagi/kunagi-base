(ns mui-commons.graphed.data-view
  (:require
   [mui-commons.graphed.api :as graphed]))


(defn value-node [v]
  {:graphed.node/name (pr-str v)
   :graphed.node/name-type (cond
                             (keyword? v) :keyword
                             (string? v) :string
                             :else nil)})


(defn map-node [m path]
  {:graphed.node/side-text "{"
   :graphed.node/childs-ids
   (mapv
    (fn [k]
      [:map-entry path k])
    (keys m))})


(defn map-entry-node [[data path k]]
  {:graphed.node/horizontal? true
   :graphed.node/childs-ids
   [[:map-entry-key path k]
    [:? (conj path k)]]})


(defn map-entry-key-node [[data path k]]
  {:graphed.node/name (pr-str k)
   :graphed.node/name-type (cond
                             (keyword? k) :keyword
                             (string? k) :string
                             :else nil)})


;; (defn map-entry-node [data path]
;;   (let [m (get-in data path)]))


(defn ?-node [data [type path]]
  (let [v (get-in data path)]
    (cond
      (map? v) (map-node v path)
      :else (value-node v))))


(defrecord DataGraphedView [data]
  graphed/GraphedView
  (root-node-id [this]
    [:? []])
  (node [this [type :as node-id]]
    (case type
      :map-entry (map-entry-node node-id)
      :map-entry-key (map-entry-key-node node-id)
      (?-node (.-data this) node-id))))
