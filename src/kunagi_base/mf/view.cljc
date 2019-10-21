(ns kunagi-base.mf.view)


(defprotocol View
  (node-tree [this editor entity-id]))
