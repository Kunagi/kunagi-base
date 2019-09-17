(ns kunagi-base.event-sourcing.fs-aggregator
  (:require
   [kunagi-base.event-sourcing.aggregator :as aggregator]))


(defonce !agents (atom {}))


(defn- new-agent []
  (agent nil
         :error-handler
         (fn [ag ex] (tap> [:err ::agent-failed ex]))))


(defn- get-agent [path]
  (locking !agents
    (if-let [ag (get-in @!agents path)]
      ag
      (let [ag (new-agent)]
        (swap! !agents assoc-in path ag)
        ag))))


(defn send-off-to [path update-f]
  (let [!ag (get-agent path)]
    (send-off !ag update-f)))


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
  (send-off-to aggregate (partial store-tx aggregate tx callback-f)))



(defrecord FsAggregator [aggregator-id]
  aggregator/Aggregator

  (store-tx [this aggregate tx callback-f]
    (store-tx! aggregate tx callback-f)))
