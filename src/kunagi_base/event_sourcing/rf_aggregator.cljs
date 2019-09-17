(ns kunagi-base.event-sourcing.rf-aggregator
  (:require
   [re-frame.core :as rf]
   [kunagi-base.event-sourcing.aggregator :as aggregator]))


(defrecord RfAggregator [model]
  aggregator/Aggregator

  (store-tx [this aggregate tx callback-f]
    (rf/dispatch [::store-tx aggregate tx callback-f])))


(defn- new-aggregate []
  {:txs []})


(defn- append-tx [aggregate tx]
  (let [aggregate (or aggregate (new-aggregate))
        tx-id (-> tx :id)
        timestamp (-> tx :timestamp)]
    (-> aggregate
        (update :txs conj tx)
        (assoc :tx-id tx-id)
        (assoc :timestamp timestamp))))



(rf/reg-event-db
 ::store-tx
 (fn [db [_ [aggregate-ident aggregate-id] tx callback-f]]
   (callback-f) ;; TODO move callback to effect
   (update-in db
              [:event-sourcing/aggregates aggregate-ident aggregate-id]
              append-tx
              tx)))


(rf/reg-sub
  :event-sourcing/aggregates
  (fn [db _]
    (get db :event-sourcing/aggregates)))
