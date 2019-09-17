(ns kunagi-base.event-sourcing.impls
  (:require
   [re-frame.core :as rf]
   [kunagi-base.event-sourcing.rf-aggregator :as aggregator]))


(defn new-aggregator [aggregator]
  (aggregator/->RfAggregator (-> aggregator :aggregator/id)))


(defn update-projection [aggregate projection-ident update-f]
  (rf/dispatch [::update-projection aggregate projection-ident update-f]))


(rf/reg-event-db
 ::update-projection
 (fn [db [_ [aggregate-ident aggregate-id] projection-ident update-f]]
   (tap> [:!!! ::update-projection {:agg-ident aggregate-ident
                                    :agg-id aggregate-id
                                    :proj projection-ident
                                    :f update-f}])
   (update-in db
              [:event-sourcing/projections aggregate-ident projection-ident aggregate-id]
              update-f)))


(rf/reg-sub
 :event-sourcing/projections
 (fn [db _]
   (get db :event-sourcing/projections)))
