(ns kunagi-base.modules.event-sourcing.aggregator)


(defprotocol Aggregator
  (store-tx [this aggregate tx callback-f]))
  ;;k(stream-txs [this aggregate tx-id callback-f]))
