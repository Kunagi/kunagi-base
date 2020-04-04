(ns mui-commons.graphed.api)


(defprotocol GraphedView
  (root-node-id [this])
  (node [this node-id]))


(defn new-buffer [view]
  {::identifier ::identifier
   :view view})


(defn buffers-root-node-id [buffer]
  (-> buffer :view root-node-id))


(defn buffers-node [buffer node-id]
  (-> buffer :view (node node-id)))
