(ns kunagi-base.event-sourcing.fs-aggregator
  (:require
   [kunagi-base.event-sourcing.aggregator :as aggregator]))






(defonce !aggregate-agents (atom {}))



(defn- new-aggregate-agent []
  (agent nil
         :error-handler
         (fn [ag ex] (tap> [:err ::store-tx-failed ex]))))


(defn- get-agent [aggregate]
  (locking !aggregate-agents
    (if-let [aggregate-agent (get-in @!aggregate-agents aggregate)]
      aggregate-agent
      (let [aggregate-agent (new-aggregate-agent)]
        (swap! !aggregate-agents assoc-in aggregate aggregate-agent)
        aggregate-agent))))


(def base-path "app-data/event-sourcing/aggregates")

(defn- filename [o]
  (cond
    (string? o) o
    (simple-keyword? o) (name o)
    (qualified-keyword? o) (str (namespace o) "/" (name o))
    :else (str o)))


(defn aggregate-path [[aggregate-ident aggregate-id]]
  (str base-path
       "/" (filename aggregate-ident)
       "/" (filename aggregate-id)))


(defn- new-agent-value [aggregate]
  (let [path (aggregate-path aggregate)]
    (-> path java.io.File. .mkdirs)
    {:aggregate aggregate
     :path path}))


(defn store-tx
  [aggregate tx callback-f ag]
  (let [ag (or ag (new-agent-value aggregate))
        path (-> ag :path)
        txlog-path (str path "/txs.edn")
        s (str (pr-str tx) "\n\n")]
    (tap> [:dbg ::appending-to-txlog txlog-path])
    (spit txlog-path s :append true)
    (callback-f)
    ag))


(defn store-tx!
  [aggregate tx callback-f]
  (let [ag (get-agent aggregate)]
    (send-off ag (partial store-tx aggregate tx callback-f))))



(defrecord FsAggregator [aggregator-id]
  aggregator/Aggregator

  (store-tx [this aggregate tx callback-f]
    (store-tx! aggregate tx callback-f)))
