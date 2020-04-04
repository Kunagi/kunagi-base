(ns kunagi.redakti.buffer)


(defn path-without-last
  "Remove the last element of a `path`.
  `(path-without-last [1 0 5]) -> [1 0]`"
  [path]
  (when path
    (->> path reverse rest reverse (into []))))


(defn node-by-path
  "Returns the child node of `node` identified by `path`"
  [node path]
  (when node
    (if (empty? path)
      node
      (let [idx (first path)
            child (-> node :redakti.node/nodes (nth idx))]
        (when child
          (node-by-path child (rest path)))))))


(defn node-under-cursor
  [buffer]
  (node-by-path (-> buffer :tree) (-> buffer :cursor)))


(defn path-parent [path tree]
  (if (empty? path)
    nil
    (path-without-last path)))


(defn path-first-child [path tree]
  (if (empty? (-> (node-by-path tree path) :redakti.node/nodes))
    nil
    (conj path 0)))


(defn path-prev [path tree]
  (if (empty? path)
    nil
    (let [idx (last path)]
      (if (= 0 idx)
        (path-parent path tree)
        (assoc path (-> path count dec) (dec idx))))))


(defn path-next [path tree]
  (if (empty? path)
    nil
    (let [idx (last path)
          parent-path (path-without-last path)
          limit (-> (node-by-path tree parent-path) :redakti.node/nodes count dec)]
      (if (>= idx limit)
        (path-parent path tree)
        (assoc path (-> path count dec) (inc idx))))))


(defn path-down [path tree]
  (path-next path tree))


(defn path-up [path tree]
  (path-prev path tree))


(defn path-left [path tree]
  (path-parent path tree))


(defn path-right [path tree]
  (path-first-child path tree))



;;; creating / updating

(defn leaf [& component]
  {:redakti.node/type :leaf
   :redakti.node/component component})


(defn column [& nodes]
  {:redakti.node/type :column
   :redakti.node/nodes nodes})


(defn dummy-tree []
  (column
   (leaf)
   (column
    (leaf)
    (leaf)
    (leaf))
   (leaf)
   (leaf)))


(defn new-buffer [root-node]
  {:cursor []
   :actions {}
   :tree root-node})


(defn reg-action [buffer action]
  (assoc-in buffer [:actions (-> action :ident)] action))

