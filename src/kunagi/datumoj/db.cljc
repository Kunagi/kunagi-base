(ns kunagi.datumoj.db)


(defprotocol Db
  (schema [this])
  (entities [this entity-ident]))
