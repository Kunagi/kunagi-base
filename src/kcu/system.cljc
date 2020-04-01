(ns kcu.system
  (:require
   #?(:cljs [reagent.core :as r])

   [kcu.utils :as u]
   [kcu.eventbus :as eventbus]
   [kcu.aggregator :as aggregator]))


(defprotocol Transactor
  (new-bucket [this])
  (transact [this bucket f]))


(defn new-atom-transactor [atom]
  (reify Transactor
    (new-bucket [_this] (atom nil))
    (transact [_this bucket f]
      (swap! bucket f)
      bucket)))


(defn on-agent-error [_agent ex]
  (tap> [:err ::on-agent-error ex]))


#?(:clj
   (defn new-agent-transactor []
     (reify Transactor
       (new-bucket [_this] (agent nil
                                  :error-mode :continue
                                  :error-handler on-agent-error))
       (transact [_this bucket f]
         (send-off bucket f)
         (await bucket)
         bucket))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn new-system
  [options]
  (let [transactor (or (-> options :transactor)
                       #?(:clj (new-agent-transactor)
                          :cljs (new-atom-transactor r/atom)))]
    {:options (assoc options
                     :transactor transactor)}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; events


(defn dispatch-event [system event]
  (-> system
      (assoc :exec {:event event})
      lookup-aggregator
      lookup-aggregate-id
      init-aggregate-bucket
      trigger-command-transaction))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; aggregators and commands


(defn- lookup-aggregator [system]
  (let [aggregator (aggregator/aggregator-by-command
                    (-> system :exec :command :command/name))]
    (-> system
        (assoc-in [:exec :aggregator] aggregator)
        (assoc-in [:exec :aggregator-id] (-> aggregator :id)))))


(defn- lookup-aggregate-id [system]
  (let [aggregate-id nil] ;; FIXME
    (-> system
        (assoc-in [:exec :aggregate-id] aggregate-id))))


(defn- init-aggregate-bucket [system]
  (let [bucket-path [:aggregate-buckets
                     (-> system :exec :aggregator-id)
                     (-> system :exec :aggregate-id)]
        bucket (or (get-in system bucket-path)
                   (let [bucket (new-bucket (-> system :options :transactor))]
                     (tap> [:!!! ::new-bucket-created bucket-path])
                     (add-watch bucket ::aggregate-watch
                                (fn [_k _ref old-value new-value]
                                  (when (not= old-value new-value)
                                    (tap> [:!!! ::aggregate-updated new-value]))))
                                  ;; FIXME (on-aggregate-updated)))
                     bucket))]
    (-> system
        (assoc-in bucket-path bucket)
        (assoc-in [:exec :aggregate-bucket] bucket))))


(defn- command-transaction-update-function
  [storage command aggregator aggregate-id aggregate]
  (let [storage-key (when storage [(-> aggregator :id) aggregate-id])
        aggregate (if aggregate
                    aggregate
                    (if storage
                      (u/load-value storage
                                    storage-key
                                    #(aggregator/new-aggregate aggregator))
                      (aggregator/new-aggregate aggregator)))
        command [(-> command :command/name name keyword) command]]
    (let [aggregate (aggregator/execute-command aggregator aggregate command)]
      (when storage (u/store-value storage storage-key aggregate))
      aggregate)))



(defn- trigger-command-transaction [system]
  (#?(:clj future :cljs do)
    (let [transactor (-> system :options :transactor)
          bucket (get-in system [:exec :aggregate-bucket])
          !aggregate (transact
                      transactor
                      bucket
                      (partial command-transaction-update-function
                               (-> system :options :aggregate-storage)
                               (-> system :exec :command)
                               (-> system :exec :aggregator)
                               (-> system :exec :aggregate-id)))
          ;; _(tap> [:!!! ::bucket !aggregate])
          aggregate @!aggregate
          command-exec (-> aggregate :exec)
          aggregate (dissoc aggregate :exec)]
      (-> system
          (update-in [:exec] merge command-exec)
          (assoc-in [:exec :aggregate] aggregate)
          #?(:cljs atom)))))


(defn dispatch-command
  [system command]
  (-> system
      (assoc :exec {:command command})
      lookup-aggregator
      lookup-aggregate-id
      init-aggregate-bucket
      trigger-command-transaction))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(tap> :!!!!!!!!!!!!!!!!!!!!!!!!!!1)
(-> (new-system {:aggregate-storage
                 (reify u/Storage
                   (u/store-value [this k v]
                     (tap> [:!!! ::store v]))
                   (u/load-value [this k constructor]
                     (constructor)))})
    (dispatch-command {:command/name :wartsapp/ziehe-nummer
                       :patient/id "patient-1"})
    deref
    (dispatch-command {:command/name :wartsapp/ziehe-nummer
                       :patient/id "patient-2"})
    deref
    :exec
    :effects)
