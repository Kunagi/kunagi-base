(ns kunagi-base.event-sourcing.api
  (:require
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am]
   [kunagi-base.auth.api :as auth]
   [kunagi-base.events :as ev]
   [kunagi-base.event-sourcing.projection :as projection]
   [kunagi-base.event-sourcing.aggregator :as aggregator]
   [kunagi-base.event-sourcing.impls :as impls]))


(am/def-module
  {:module/id ::event-sourcing})


(am/def-extension
  {:schema {:aggregator/module {:db/type :db.type/ref}
            :aggregator/id {:db/unique :db.unique/identity}
            :projector/module {:db/type :db.type/ref}
            :projector/id {:db/unique :db.unique/identity}
            :projector/aggregator {:db/type :db.type/ref}
            :command/module {:db/type :db.type/ref}
            :command/id {:db/unique :db.unique/identity}
            :command/ident {:db/unique :db.unique/identity}
            :command/aggregator {:db/type :db.type/ref}}})


(defn def-aggregator [aggregator]
  (am/register-entity
   :aggregator
   (-> aggregator
       (assoc :aggregator/impl (impls/new-aggregator aggregator)))))


(defn def-projector [projector]
  (am/register-entity :projector projector))


(defn def-command [command]
  (am/register-entity :command command))


(defn- aggregate-ident->aggregator-id [aggregate-ident]
  (let [module-ident (keyword (namespace aggregate-ident))
        aggregator-ident (keyword (name aggregate-ident))]
    (first
     (am/q!
      '[:find [?e]
        :in $ ?aggregator-ident ?module-ident
        :where
        [?e :aggregator/ident ?aggregator-ident]
        [?e :aggregator/module ?m]
        [?m :module/ident ?module-ident]]
      [aggregator-ident module-ident]))))


(defn- aggregator-for-aggregate [aggregate]
  (let [[aggregate-ident aggregate-id] aggregate
        aggregator-id (aggregate-ident->aggregator-id aggregate-ident)]
    (am/entity! aggregator-id)))


;; (defn- update-projection-by-txs [projector aggregate projection]
;;   (let [
;;         projection (or projection
;;                        {:tx-id nil
;;                         :payload nil})
;;         tx-id (-> projection :tx-id)

;;         aggregator-impl (-> projector :projector/aggregator :aggregator/impl)
;;         apply-event-f (-> projector :projector/apply-event-f)
;;         new-txs (aggregator/txs-since aggregator-impl aggregate tx-id)]
;;     (reduce
;;      (fn [projection tx]
;;        (assoc
;;         (reduce
;;          (fn [projection event]
;;            (update projection :payload apply-event-f event))
;;          projection
;;          (-> tx :events))
;;         :tx-id tx-id))
;;      projection
;;      new-txs)))


(defn- update-projection-for-projector [aggregate projector]
  (let [
        aggregator-impl (-> projector :projector/aggregator :aggregator/impl)]))


(defn- update-projection [apply-event-f txs projection]
  (projection/apply-txs apply-event-f projection txs))


(defn- apply-txs-to-aggregates-projection [txs aggregate projector]
  (let [apply-event-f (-> projector :projector/apply-event-f)
        projector-ident (-> projector :projector/ident)]
    (impls/update-projection aggregate
                             projector-ident
                             (partial update-projection apply-event-f txs))))


(defn- apply-txs-to-aggregates-projections [txs aggregate]
  (let [aggregator (aggregator-for-aggregate aggregate)
        projectors (-> (aggregator-for-aggregate aggregate)
                       :projector/_aggregator)]
    (doseq [projector projectors]
      (apply-txs-to-aggregates-projection txs aggregate projector))))


(defn- on-transacted [tx aggregate callback-f]
  (when callback-f (callback-f))
  (apply-txs-to-aggregates-projections [tx] aggregate))


(defn aggregate-events
  ([aggregate events]
   (aggregate-events aggregate events nil))
  ([aggregate events callback-f]
   (let [tx {:timestamp (utils/current-time-millis)
             :id (utils/new-uuid)
             :events events}
         aggregator (aggregator-for-aggregate aggregate)
         _ (when-not aggregator (throw (ex-info (str "No aggregator for aggregate: " aggregate)
                                                {})))
         aggregator-impl (-> aggregator :aggregator/impl)
         tx-callback-f (partial on-transacted tx aggregate callback-f)]
     (aggregator/store-tx aggregator-impl aggregate tx tx-callback-f))))


(defn- command-execution-permitted? [command context]
  (if (auth/context-authorized? context)
    true
    (let [req-perms (-> command :command/req-perms)]
      (if (nil? req-perms)
        false
        (auth/context-has-permissions? context req-perms)))))


(defn- projection-for-command [context aggregate projector]
  (-> context
      (impls/projection-from-context aggregate (-> projector :projector/ident))
      :payload))


(defn- projections-for-command [context aggregate command]
  (let [projectors (-> command :command/aggregator :projector/_aggregator)]
    (reduce
     #(assoc %1 (-> %2 :projector/ident) (projection-for-command context aggregate %2))
     {}
     projectors)))


(defn- execute-command-f [aggregate command command-v context]
  (let [f (-> command :command/f)
        context (assoc context :aggregate aggregate)
        context (assoc context :projections (projections-for-command context aggregate command))
        command-result (f command-v context)
        events (-> command-result :events)
        action-f (-> command-result :action-f)]
    (aggregate-events aggregate events action-f)))


(defn execute-command! [aggregate command-v context]
  (let [[command-ident] command-v
        command (am/entity! [:command/ident command-ident])]
    ;; (tap> [:!!! ::command {:command command
    ;;                        :command-ident command-ident}])
    (if-not (command-execution-permitted? command context)
      (do
       (tap> [:inf ::command-dispatch-denied {:command command-v
                                              :user (-> context :auth/user-id)}])
       (if-let [response-f (-> context :comm/response-f)]
         (response-f [:auth/server-command-not-permitted command-v])))
      (execute-command-f aggregate command command-v context))))


(defn on-command-triggered [[_ aggregate command] context]
  ;; TODO assert argss
  (execute-command! aggregate command context))


(ev/def-event-handler
  {:event-handler/id ::command
   :event-handler/event-ident :kunagi-base/command-triggered
   :event-handler/f on-command-triggered})
