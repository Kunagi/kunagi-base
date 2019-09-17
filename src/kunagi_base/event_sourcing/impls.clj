(ns kunagi-base.event-sourcing.impls
  (:require
   [kunagi-base.event-sourcing.fs-aggregator :as aggregator]))


(defn new-aggregator [aggregator]
  (aggregator/->FsAggregator (-> aggregator :aggregator/id)))


(defn update-projection [aggregate projection-ident update-f])
  ;; FIXME
