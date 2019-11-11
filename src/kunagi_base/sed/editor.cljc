(ns kunagi-base.sed.editor)


(defn new-editor []
  {:node-action-handler nil
   :root-node {:text "editor"}})


(defn expand-node-tree [node]
  (update (if (fn? node) (node) node) :children (partial map expand-node-tree)))


(defn activate-view [editor {:keys [root-node
                                    node-action-handler]}]
  (-> editor
      (assoc :root-node (expand-node-tree root-node))
      (assoc :node-action-handler node-action-handler)))


(defn node-action-handler [editor]
  (-> editor :node-action-handler))
