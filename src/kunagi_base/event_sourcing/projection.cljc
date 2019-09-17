(ns kunagi-base.event-sourcing.projection)


(defn apply-event-to-payload [apply-event-f payload event]
  (apply-event-f payload event))


(defn apply-events-to-payload [apply-event-f payload events]
  (reduce
   (partial apply-event-to-payload apply-event-f)
   payload
   events))


(defn apply-tx [apply-event-f projection tx]
  (-> projection
      (update :payload
              (partial apply-events-to-payload apply-event-f)
              (-> tx :events))
      (assoc :tx-id (-> tx :id))))


(defn apply-txs [apply-event-f projection txs]
  (reduce
   (partial apply-tx apply-event-f)
   projection
   txs))
