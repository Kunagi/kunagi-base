(ns kunagi-base.event-sourcing.impls
  (:require
   [kunagi-base.event-sourcing.fs-aggregator :as aggregator]
   [kunagi-base.event-sourcing.fs-projector :as projector]))


(defn new-aggregator [aggregator]
  (aggregator/->FsAggregator (-> aggregator :aggregator/id)))


(defn update-projection [aggregate projection-ident update-f]
  (projector/update-projection aggregate projection-ident update-f))
