(ns kcu.system
  (:require
   #?(:cljs [reagent.core :as r])

   [kcu.utils :as u]
   [kcu.registry :as registry]
   [kcu.eventbus :as eventbus]
   [kcu.aggregator :as aggregator]
   [kcu.projector :as projector]))


(defn transactions [system]
  (->> system :transactions deref vals (sort-by :id)))


(defn- update-transaction [system tx-id f]
  (swap! (-> system :transactions)
         update tx-id f))


(defn log-error
  ([system error-type data]
   (log-error system error-type data nil))
  ([system error-type data ex]
   (tap> [:err ::system-error error-type {:system (-> system :id)
                                          :error-data data
                                          :exception ex}])
   (when ex
     (tap> [:err ::system-error-exception ex]))))
   ;; TODO store errors in atom


(u/do-once

  (defprotocol AggregateStorage
    (load-aggregate-value [this aggregator-id aggregate-id])
    (store-aggregate-effects [this aggregator-id aggregate-id effects])
    (store-aggregate-value [this aggregator-id aggreagate-id value]))


  (defprotocol ProjectionStorage
    (load-projection-value [this projector-id projection-id])
    (store-projection-value [this projector-id projection-id value]))


  (defprotocol Transactor
    (new-bucket [this])
    (transact [this bucket f])))


(defn new-atom-transactor [atom]
  (reify Transactor
    (new-bucket [_this] (atom nil))
    (transact [_this bucket f]
      (swap! bucket f)
      bucket)))


#?(:clj
   (defn new-agent-transactor [system]
     (reify Transactor
       (new-bucket [_this] (agent nil
                                  :error-mode :continue
                                  :error-handler
                                  (fn [agent ex]
                                    (log-error system
                                               :transactor-failed
                                               {:agent agent}
                                               ex))))
       (transact [_this bucket f]
         (send-off bucket f)
         bucket))))


(defn- transaction-bucket [system bucket-type entity-type entity-id watch-f]
  (let [!buckets (get-in system [:buckets bucket-type])]
    (locking !buckets
      (if-let [bucket (get @!buckets [entity-type entity-id])]
        bucket
        (let [transactor (-> system :transactor)
              bucket (new-bucket transactor)]
          (when watch-f (add-watch bucket ::watch watch-f))
          (swap! !buckets assoc [entity-type entity-id] bucket)
          bucket)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; events


(defn- store-events [system events]
  ;; TODO store events
  system)


(defn dispatch-events [system events]
  (store-events system events)
  (doseq [event events]
    (eventbus/dispatch! (-> system :eventbus)
                        event
                        {:system system}))
  system)


(defn dispatch-event [system event]
  (dispatch-events system [event]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; aggregators and commands

(defn aggregate-bucket [system aggregator-id aggregate-id]
  (transaction-bucket system :aggregate aggregator-id aggregate-id nil))


(defn aggregate [system aggregator-id aggregate-id]
  @(aggregate-bucket system aggregator-id aggregate-id))


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


(defn- reify-aggregate-effect [system aggregator effect]
  (cond

    (contains? effect :event/name)
    (dispatch-event system (assoc effect
                                  :event/name
                                  (registry/as-global-keyword
                                   (-> effect :event/name)
                                   (-> aggregator :bounded-context))))

    :else
    (log-error system :unsupported-effect {:effect effect})))


(defn- reify-aggregate-effects [system aggregator tx]
  (doseq [effect (-> tx :effects)]
    (reify-aggregate-effect system aggregator effect)))


(defn- command-transaction-update-function
  "The aggregate update function which is running inside the transaction
  (agent or atom)."
  [system tx aggregate]
  (let [storage (-> system :options :aggregate-storage)
        command (-> tx :command)
        aggregator (-> tx :aggregator)
        aggregate-id (-> tx :aggregate-id)
        aggregator-id (-> aggregator :id)

        aggregate (or aggregate
                      (when storage (load-aggregate-value storage
                                                          aggregator-id
                                                          aggregate-id))
                      (aggregator/new-aggregate aggregator))
        aggregate (aggregator/execute-command aggregator aggregate command)
        tx (get aggregate :exec)
        aggregate (dissoc aggregate :exec)
        tx (assoc tx :aggregate aggregate)]
    (when storage
      (store-aggregate-effects storage
                               aggregator-id aggregate-id
                               (-> tx :effects))
      (store-aggregate-value storage
                             aggregator-id aggregate-id aggregate))
    (reify-aggregate-effects system aggregator tx)
    (update-transaction system (-> tx :tx-id) #(merge % tx))
    aggregate))


(defn- trigger-command-transaction [system]
  (let [transactor (-> system :transactor)
        bucket (aggregate-bucket system
                                 (-> system :exec :aggregator-id)
                                 (-> system :exec :aggregate-id))]
    (transact transactor bucket
              (partial command-transaction-update-function
                       system
                       (-> system :exec)))
    system))


(defn dispatch-command
  [system command]
  (-> system
      (assoc :exec {:command command})
      lookup-aggregator
      lookup-aggregate-id
      trigger-command-transaction))


(defn dispatch-commands [system commands]
  (reduce dispatch-command system commands))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; projectors


(defn- on-projection-updated [system _ _ _ projection]
  (when-let [tx-id (get projection :aggregate/tx-id)]
    ;; (throw (ex-info "BOOM" {:tx-id tx-id}))
    (update-transaction system tx-id
                        #(assoc-in %
                                   [:projections
                                    [(-> projection :projection/projector)
                                     (-> projection :projection/id)]]
                                   projection))))


(defn projection-bucket [system projector-id projection-id]
  (transaction-bucket system :projection
                      projector-id projection-id
                      (partial on-projection-updated system)))


(defn projection [system projector-id projection-id]
  @(projection-bucket system projector-id projection-id))


(defn loaded-projections [system]
  (->> system
       :buckets
       :projection
       deref
       keys
       (map #(projection system (first %) (second %)))))


(defn subscribe-to-projection [system projector-id projection-id on-change-f]
  (u/assert-spec ::projector/projector-id projector-id)
  (let [bucket (projection-bucket system projector-id projection-id)
        watch-id (u/random-uuid)]
    (add-watch bucket watch-id
               (fn [_ _ old-value new-value]
                 (when (not= old-value new-value)
                   (on-change-f new-value (fn unsubscribe []
                                            (remove-watch bucket watch-id))))))))


(defn merge-projection [system projector-id projection-id new-value]
  (u/assert-spec ::projector/projector-id projector-id)
  (let [bucket (projection-bucket system projector-id projection-id)
        storage (-> system :options :projection-storage)]
    (reset! bucket new-value)
    (when storage
      (store-projection-value storage
                              projector-id projection-id new-value))))


(defn- projection-transaction-update-function
  [system projector handler projection-id tx-id event projection]
  (let [storage (-> system :options :projection-storage)
        projector-id (-> projector :id)
        projection (or projection
                       (when storage
                         (load-projection-value
                          storage projector-id projection-id)))
        projection (projector/apply-event projector
                                          handler
                                          projection-id
                                          projection
                                          event)
        projection (assoc projection :aggregate/tx-id tx-id)]
    (when storage
      (store-projection-value storage
                              projector-id projection-id projection))
    projection))


(defn- handle-projector-event [system projector event]
  (let [event (assoc event :event/name (-> event :event/name name keyword))
        tx-id (-> event :aggregate/tx-id)]
    (when-let [handler (projector/handler-for-event projector event)]
      (let [projection-id (projector/projection-id projector handler event)
            transactor (-> system :transactor)
            bucket (projection-bucket system (-> projector :id) projection-id)]
        (transact transactor bucket
                  (partial projection-transaction-update-function
                           system projector handler projection-id tx-id event))))))


(defn- projectors-event-handler [system event]
  (let [projectors (projector/projectors-by-event (-> event :event/name))]
    (doseq [projector projectors]
      ;; TODO try catch
      (#?(:clj future :cljs do)
       (handle-projector-event system projector event)))))



(defn- register-projectors-event-handler [system]
  (let [handler {:id ::projector
                 :event :event-handler/catch-all
                 :f (partial projectors-event-handler system)
                 :options {}}]
    (-> system
        (update :eventbus eventbus/add-handler handler))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; init

(defn- init-transactor [system]
  (let [transactor #?(:clj (new-agent-transactor system)
                      :cljs (new-atom-transactor r/atom))]
    (-> system
        (assoc :transactor transactor))))


(defn- init-eventbus [system]
  (let [eventbus (eventbus/new-eventbus)]
    (-> system
        (assoc :eventbus eventbus))))


(defn new-system
  [id options]
  (tap> [:inf ::new-system id])
  (-> {:id id
       :options options
       :buckets {:projection (atom {})
                 :aggregate (atom {})}
       :transactions (atom {})}
      init-transactor
      init-eventbus
      register-projectors-event-handler))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



#_(def system (new-system :repltest {}))

#_(-> system :projection-buckets deref)


#_(dispatch-command system
                    {:command/name :wartsapp/ziehe-nummer
                     :patient/id "patient-1"})


#_
(do

  (dispatch-event system
                  {:event/name :wartsapp/nummer-gezogen
                   :patient/id "patient-1"
                   :nummer "abc"}))
