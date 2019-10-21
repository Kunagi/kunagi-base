(ns kunagi-base.mf.extensions.root
  (:require
   [kunagi-base.mf.editor :as editor]
   [kunagi-base.mf.extension :as extension]

   [kunagi-base.mf.extensions.root.views.entities :as entities]))



(defrecord Extension [ident]
  extension/Extension
  (views [this]
    [(entities/new-view)]))


(defn new-root-extension []
  (->Extension :root))
