(ns kcu.txa
  "Transaction Aggregate"
  (:refer-clojure :exclude [read load])
  (:require
   [kcu.utils :as u]
   [kcu.files :as files]
   [kcu.aggregator :as aggregator]
   [kcu.query :as query]))


(defn on-agent-error [id _agent ex]
  (tap> [:err ::on-agent-error ex]))


(defn- default-store-f [txa value]
  (let [file (-> txa :options :file)]
    (tap> [:dbg ::store file])
    (files/write-edn file value)))


(defn- default-load-f [txa]
  (let [file (-> txa :options :file)]
    (tap> [:dbg ::load file])
    (files/read-edn file)))


(defn- complete-options [options]
  (cond-> options

          ;; default store-f
          (and (get options :file) (not (get options :store-f)))
          (assoc :store-f default-store-f)

          ;; default load-f
          (and (get options :file) (not (get options :load-f)))
          (assoc :load-f default-load-f)))


(defn new-txa
  [id options]
  {:id id
   :agent (agent {:loaded? false
                  :payload nil
                  :num 0
                  :transaction-exceptions '()}
                 :error-mode :continue
                 :error-handler (partial on-agent-error id))
   :options (complete-options options)})


(defmacro def-txa
  [sym options]
  (let [id (keyword (str (ns-name *ns*)) (str sym))]
    `(defonce ~sym (new-txa ~id ~options))))


(defn loaded?
  [txa]
  (-> txa :agent deref :loaded?))


(defn- load-payload [txa]
  (if-let [payload (when-let [load-f (-> txa :options :load-f)]
                     (try
                       (load-f txa)
                       (catch Exception ex
                         (throw (ex-info (str "Loading aggregate `"
                                              (-> txa :id)
                                              "` failed.")
                                         {:txa txa}
                                         ex)))))]
    payload
    (when-let [constructor (-> txa :options :constructor)]
      (constructor txa))))


(declare transact)

(defn read-and-deliver
  [txa value-promise]
  (try
    (transact
     txa
     (fn [value]
       (deliver value-promise value)
       value))
    value-promise
    (catch Exception ex
      (throw (ex-info (str "read-and-deliver in txa `" (-> txa :id) "` failed.")
                      {:txa txa}
                      ex)))))


(defn read
  [txa]
  (let [value (-> txa :agent deref)]
    (if (-> value :loaded?)
      (-> value :payload)
      (let [value-promise (promise)]
        (read-and-deliver txa value-promise)
        @value-promise))))


(defn- store-new-payload [txa old-value new-value]
  (when-let [store-f (-> txa :options :store-f)]
    (let [new-value (reduce dissoc new-value (-> txa :options :store-exclude-keys))
          old-value (reduce dissoc old-value (-> txa :options :store-exclude-keys))]
      (when (not= old-value new-value)
        (try
          (store-f txa new-value)
          (catch Exception ex
            (throw (ex-info (str "Storing new value failed.")
                            {:store-f store-f
                             :new-value new-value}
                            ex))))))))


(defn transact
  [txa update-f]
  (send-off
   (-> txa :agent)
   (fn [value]
     (try
       (let [old-payload (if (-> value :loaded?)
                           (get value :payload)
                           (load-payload txa))
             new-payload (update-f old-payload)]
         (store-new-payload txa old-payload new-payload)
         (-> value
             (assoc :loaded? true)
             (assoc :payload new-payload)
             (update :num inc)))
       (catch Exception ex
         (throw (ex-info (str "Transaction in aggregate `"
                              (-> txa :id)
                              "` failed.")
                         {:txa txa
                          :update-f update-f}
                         ex))))))
  txa)


(defn transact-sync [txa update-f]
  (transact txa update-f)
  (await (-> txa :agent))
  txa)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce !txas (atom {}))


(defn txa
  [id]
  (get @!txas id))


(defn reg-txa
  [id options]
  (swap! !txas assoc id (new-txa id options))
  id)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; durable aggregator txa


(defn- aggregate-dir [aggregator entity-id]
  (str "app-data"
       "/aggregates/"
       (-> aggregator :id name)
       (when entity-id
         (str "/" entity-id))))


(defn- load-aggregator-aggregate
  [txa]
  (let [aggregator (aggregator/aggregator (-> txa :options ::aggregator-id))
        entity-id (-> txa :options ::aggregate-entity-id)
        dir (aggregate-dir aggregator entity-id)
        projections-dir (str dir "/projections")
        projections-dir-file (java.io.File. projections-dir)]
    (tap> [:dbg ::load-aggregate dir])
    (if-not (-> projections-dir-file .exists)
      (aggregator/new-aggregate aggregator)
      (reduce (fn [aggregate file]
                (if (-> file .isDirectory)
                  (aggregator/assign-projections
                   aggregator aggregate (files/read-entities file))
                  (if-not (-> file .getName (.endsWith ".edn"))
                    aggregate
                    (aggregator/assign-projection
                     aggregator aggregate (files/read-edn file)))))
              (aggregator/new-aggregate aggregator)
              (-> projections-dir-file .listFiles)))))


(defn- store-aggregator-aggregate
  [txa aggregate]
  (let [aggregator (aggregator/aggregator (-> txa :options ::aggregator-id))
        entity-id (-> txa :options ::aggregate-entity-id)
        dir (aggregate-dir aggregator entity-id)
        p-refs (-> aggregate :exec :projection-results keys)]
    ;; TODO store events
    (doseq [[projector-id projection-entity-id :as p-ref] p-refs]
      (let [file (str dir "/projections/"
                      (name projector-id)
                      (when projection-entity-id
                        (str "/" projection-entity-id))
                      ".edn")
            projection (get-in aggregate [:projections p-ref])]
        (tap> [:dbg ::store-projection file])
        (files/write-edn file projection)))))


(defn reg-durable-aggregator-txa
  [aggregator-id options]

  ;; FIXME check permissions
  (doseq [projector-id (-> options :projectors-as-responders)]
    (query/reg-responder
     (keyword (name aggregator-id) (name projector-id))
     {:f (fn [_context {:keys [id]}]
           (let [txa (txa aggregator-id)
                 aggregate (read txa)]
             (aggregator/projection aggregate projector-id id)))}))

  (reg-txa
   aggregator-id
   {
    ::aggregator-id aggregator-id
    ::aggregate-entity-id nil

    :load-f
    (fn [txa] (load-aggregator-aggregate txa))

    :store-f
    (fn [txa value] (store-aggregator-aggregate txa value))}))


(defn trigger-aggregator-command!
  [aggregator-id command]
  (u/assert-spec ::aggregator/aggregator-id aggregator-id)
  (u/assert-spec ::aggregator/command command)
  (tap> [:inf ::command aggregator-id command])
  (let [txa (txa aggregator-id)
        aggregator (aggregator/aggregator aggregator-id)]
    (transact
     txa
     (fn [old-aggregate]
       (let [aggregate (aggregator/execute-command aggregator old-aggregate command)]
         aggregate)))))


(defn trigger-command!
  [txa-id command]
  (trigger-aggregator-command! txa-id command))
