(ns kunagi-base.modules.event-sourcing.impls
  (:require
   [kunagi-base.modules.event-sourcing.fs-aggregator :as aggregator]
   [kunagi-base.modules.event-sourcing.fs-projector :as projector]))


(defn new-aggregator [aggregator]
  (aggregator/->FsAggregator (-> aggregator :aggregator/id)))


(defn update-projection [aggregate projection-ident update-f]
  (projector/update-projection aggregate projection-ident update-f))


(defn projection-from-context [context aggregate projection-ident]
  (let [projection (projector/projection aggregate projection-ident)]
    projection))
