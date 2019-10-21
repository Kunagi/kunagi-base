(ns kunagi-base.mf.extension)


(defprotocol Extension
  (views [this]))


(defn view [extension view-ident]
  (->> (views extension)
       (filter #(= view-ident (get % :ident)))
       first))
