(ns kunagi-base.event-sourcing.fs-aggregator
  (:require
   [kunagi-base.event-sourcing.aggregator :as aggregator]))


(defrecord FsAggregator [aggregator-id]
  aggregator/Aggregator

  (store-tx [this aggregate tx callback-f]
    (tap> [:!! ::aggregate-events this aggregate tx])
    (callback-f)))

