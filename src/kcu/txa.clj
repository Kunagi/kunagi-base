(ns kcu.txa
  "Transaction Aggregate"
  (:refer-clojure :exclude [read load]))


(defn on-agent-error [id _agent ex]
  (tap> [:err ::on-db-agent-error id ex]))


(defn new-txa
  [id options]
  {:id id
   :agent (agent {:loaded? false
                  :payload nil
                  :num 0
                  :transaction-exceptions '()}
                 :error-mode :continue
                 :error-handler (partial on-agent-error id))
   :options options})


(defmacro def-txa
  [sym options]
  (let [id (keyword (str (ns-name *ns*)) (str sym))]
    `(defonce ~sym (new-txa ~id ~options))))


(defn loaded?
  [txa]
  (-> txa :agent deref :loaded?))


(defn- load-payload [txa]
  (when-let [load-f (-> txa :options :load-f)]
    (try
      (load-f txa)
      (catch Exception ex
        (throw (ex-info (str "Loading aggregate `"
                             (-> txa :id)
                             "` failed.")
                        {:txa txa}
                        ex))))))


(defn- load
  [txa]
  (let [agent (-> txa :agent)]
    (send-off
     (-> txa :agent)
     (fn [value]
       (if (-> value :loaded?)
         value
         (try
           (-> value
               (assoc :payload (load-payload txa))
               (assoc :loaded? true))
           (catch Exception ex
             (-> value
                 (assoc :load-exception ex)))))))
    (await agent)
    (let [value (-> agent deref)]
      (when-let [ex (-> value :load-exception)]
        (throw ex))
      (-> value :payload))))


(defn read
  [txa]
  (let [value (-> txa :agent deref)]
    (if (-> value :loaded?)
      (-> value :payload)
      (load txa))))


(defn- store-new-payload [txa old-value new-value]
  (when-let [store-f (-> txa :options :store-f)]
    (when (not= old-value new-value)
      (try
        (store-f txa new-value)
        (catch Exception ex
          (throw (ex-info (str "Storing new value failed.")
                          {:store-f store-f
                           :new-value new-value}
                          ex)))))))


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


