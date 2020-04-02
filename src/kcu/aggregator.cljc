(ns kcu.aggregator
  #?(:cljs (:require-macros [kcu.aggregator]))
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]
   [kcu.registry :as registry]
   [kcu.projector :as projector]))


(s/def ::aggregator-id simple-keyword?)

(s/def ::handler-f fn?)
(s/def ::handler-options map?)

(s/def ::command-name qualified-keyword?)

(s/def ::event-name simple-keyword?)

(defn- new-aggregator [id]
  {:id id
   :bounded-context (registry/bounded-context id)
   :command-handlers {}
   :event-handlers {}})

(defn- add-command-handler
  [aggregator handler]
  (-> aggregator
      (assoc-in [:command-handlers (-> handler :command)] handler)))

(defn- add-event-handler
  [aggregator handler]
  (-> aggregator
      (assoc-in [:event-handlers (-> handler :event)] handler)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn assert-aggregator [aggregator]
  (u/assert-entity aggregator {:id ::aggregator-id
                               :command-handlers map?}))

(defn assert-aggregate [aggregate]
  (u/assert-entity aggregate {:aggregate/aggregator ::aggregator-id}))


(defn new-aggregate
  [aggregator]
  (assert-aggregator aggregator)
  (let [aggregator-id (-> aggregator :id)]
    {:aggregate/aggregator aggregator-id
     :aggregate/tx-num 0}))


;; (defn projection
;;   [aggregate projector-id entity-id]
;;   (get-in aggregate
;;           [:projections (projector/as-projection-ref projector-id entity-id)]))


;; (defn assign-projection
;;   [aggregator aggregate projection]
;;   (assert-aggregator aggregator)
;;   (assert-aggregate aggregate)
;;   (projector/assert-projection projection)
;;   (let [projector-id (get projection :projection/projector)
;;         entity-id (get projection :projection/entity-id)]
;;     (assoc-in aggregate
;;               [:projections (projector/as-projection-ref projector-id entity-id)]
;;               projection)))


;; (defn assign-projections
;;   [aggregator aggregate projections]
;;   (reduce (partial assign-projection aggregator) aggregate projections))


;; (defn- provide-projection
;;   [aggregator aggregate projection-ref]
;;   (let [projection-ref (projector/as-projection-ref projection-ref)]
;;     (get-in aggregate [:projections projection-ref])))


(defn- register-input [inputs input-type input-args]
  {:input-type input-type
   :input-args input-args}
  (let [infos (or (get inputs input-type)
                  #{})
        infos (conj infos input-args)]
    (assoc inputs input-type infos)))


(defn- provide-random-uuid [aggregate]
  (if-let [!idx (-> aggregate :devtools/uuid-idx)]
    (let [idx (inc (get @!idx 0))
          uuid (str idx)]
      (reset! !idx idx)
      uuid)
    (u/random-uuid-string)))


(defn- context-f [aggregator aggregate !inputs]
  (fn context [input-type & input-args]
    (swap! !inputs #(conj % (if input-args
                              [input-type (into [] input-args)]
                              input-type)))
    (case input-type
      :timestamp (or (-> aggregate :timestamp)
                     (u/current-time-millis))
      :random-uuid (provide-random-uuid aggregate)
      (throw (ex-info (str "Unsupported context input `" input-type "`.")
                      {:input-type input-type
                       :input-args input-args})))))


(defn- conform-effects-from-command [effects]
  (if (map? effects)
    (conform-effects-from-command [effects])
    effects))


(defn- apply-command
  [aggregator aggregate command]
  (u/assert-entity command {:command/name ::command-name})
  (let [command-name (-> command :command/name)
        aggregator-id (-> aggregator :id)
        handler (get-in aggregator [:command-handlers command-name])
        _ (when-not handler
            (throw (ex-info (str "Executing command `"
                                 command-name
                                 "` with aggregator `"
                                 aggregator-id
                                 "` failed. No handler for command.")
                            {:aggregator-id aggregator-id
                             :unknown-command command-name
                             :known-commands (-> aggregator :command-handlers keys)})))
        f (get handler :f)
        !inputs (atom [])
        tx-num (inc (get aggregate :aggregate/tx-num))
        tx-id (u/random-uuid-string)
        tx-time (u/current-time-millis)
        aggregate (assoc aggregate :aggregate/tx-num tx-num
                                   :aggregate/tx-id tx-id
                                   :aggregate/tx-time tx-time)
        context-f (context-f aggregator aggregate !inputs)
        effects (try
                  (f aggregate command context-f)
                  (catch #?(:clj Exception :cljs :default) ex
                     (throw (ex-info (str "Executing command `"
                                      command-name
                                      "` with aggregator `"
                                      aggregator-id
                                      "` failed. Commnad handler crashed.")
                                     {:aggregator-id aggregator-id
                                      :command command}
                                     ex))))
        effects (conform-effects-from-command effects)]
      (assoc aggregate
             :exec {:command command
                    :tx-num tx-num
                    :tx-id tx-id
                    :tx-time tx-time
                    :effects effects
                    :inputs @!inputs})))


;; (defn- apply-events [aggregator aggregate projection-ref events]
;;   (let [projection-ref (projector/as-projection-ref projection-ref)
;;         projector-id (first projection-ref)
;;         projector (projector/projector projector-id)
;;         projection-path [:projections projection-ref]
;;         projection (or (get-in aggregate projection-path)
;;                        (projector/new-projection projector
;;                                                  (second projection-ref)))
;;         projection-ret (projector/project projector projection events)
;;         projection (get projection-ret :projection)]
;;     (-> aggregate
;;         (assoc-in projection-path projection)
;;         (assoc-in [:exec :projection-results projection-ref] projection-ret))))


;; (defn- project-events
;;   [aggregator aggregate]
;;   (let [events-map (get-in aggregate [:exec :effects :events])]
;;     (reduce (fn [aggregate [projection-ref events]]
;;               (apply-events aggregator aggregate projection-ref events))
;;             aggregate
;;             events-map)))


(defn event?
  [effect]
  (and (map? effect) (contains? effect :event/name)))


(defn enrich-devents [aggregate time]
  (update-in aggregate
             [:exec :effects]
             #(map (fn [effect]
                     (if-not (event? effect)
                       effect
                       (-> effect
                           (assoc :event/time time)
                           (assoc :event/id (provide-random-uuid aggregate))
                           (assoc :aggregate/aggregator (get aggregate :aggregate/aggregator))
                           (assoc :aggregate/id (get aggregate :aggregate/id))
                           (assoc :aggregate/tx-num (get aggregate :aggregate/tx-num))
                           (assoc :aggregate/tx-id (get aggregate :aggregate/tx-id))
                           (assoc :aggregate/tx-time (get aggregate :aggregate/tx-time)))))
                   %)))


(defn- apply-event [aggregator aggregate event]
  (let [event-name (-> event :event/name)
        aggregator-id (-> aggregator :id)
        handler (get-in aggregator [:event-handlers event-name])]
    (if-not handler
      aggregate
      (let [aggregate (update-in aggregate [:exec :applied-events] conj event)

            f (get handler :f)

            ;; narrow the scope inside the aggregate
            f (if-let [scope (-> aggregator :event-handlers event-name :options :scope)]
                (fn [aggregate event]
                  (update-in aggregate scope (fn [scoped-value] (f scoped-value event))))
                f)]

        (try
          (f aggregate event)
          (catch #?(:clj Exception :cljs :default) ex
            (throw (ex-info (str "Applying event `"
                                 event-name
                                 "` with aggregator `"
                                 aggregator-id
                                 "` failed. Event handler crashed.")
                            {:aggregator-id aggregator-id
                             :event event
                             :aggregate aggregate}
                            ex))))))))



(defn- apply-events [aggregator aggregate]
  (reduce (partial apply-event aggregator)
          aggregate
          (filter event? (-> aggregate :exec :effects))))


(defn execute-command
  [aggregator aggregate command]
  (assert-aggregator aggregator)
  (assert-aggregate aggregate)
  (u/assert-entity command {:command/name ::command-name})
  (let [aggregate (or aggregate
                      (new-aggregate aggregator))
        aggregate (dissoc aggregate :exec)
        aggregate (apply-command aggregator aggregate command)
        aggregate (enrich-devents aggregate (u/current-time-millis))
        aggregate (apply-events aggregator aggregate)]
        ;; aggregate (project-events aggregator aggregate)]
    aggregate))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn project-events [aggregator aggregate projectors p-pools]
  (let [events (->> aggregate :exec :effects (filter event?))
        projections (reduce
                     (fn [projections projector]
                       (into projections
                             (projector/handle-events projector
                                                      (get p-pools (-> projector :id))
                                                      events)))
                     []
                     projectors)]
    (-> aggregate
        (assoc-in [:exec :projections] projections))))


(defn simulate-commands
  [aggregator commands projectors]
  (let [p-pools (reduce (fn [p-pools projector]
                          (assoc p-pools
                                 (-> projector :id)
                                 (projector/new-projection-pool projector nil nil)))
                        {} projectors)
        ret {:aggregator aggregator
             :commands commands
             :aggregate (-> (new-aggregate aggregator)
                            (assoc :devtools/uuid-idx (atom {})))
             :flow []}]
    (->> commands
         (reduce (fn [ret command]
                   (let [aggregate (get ret :aggregate)

                         aggregate (assoc aggregate
                                          :exec {:command command})

                         time (-> ret :flow count inc)
                         aggregate (assoc aggregate
                                          :devtools/timestamp
                                          time)


                         aggregate
                         (try
                           (apply-command aggregator aggregate command)
                           (catch #?(:clj Exception :cljs :default) ex
                             (assoc-in aggregate [:exec :command-exception] ex)))

                         aggregate (enrich-devents aggregate time)

                         aggregate
                         (try
                           (apply-events aggregator aggregate)
                           (catch #?(:clj Exception :cljs :default) ex
                             (assoc-in aggregate [:exec :events-exception] ex)))


                         aggregate
                         (try
                           (project-events aggregator
                                           aggregate
                                           projectors
                                           p-pools)
                           (catch #?(:clj Exception :cljs :default) ex
                             (assoc-in aggregate [:exec :projection-exception] ex)))

                         flow (-> ret :flow)
                         exec (-> aggregate
                                  :exec
                                  (assoc :index (count flow))
                                  (assoc :aggregate (dissoc aggregate
                                                            :exec
                                                            :aggregate/aggregator
                                                            :devtools/timestamp
                                                            :devtools/uuid-idx)))
                         flow (conj flow exec)]
                     (-> ret
                         (assoc :flow flow)
                         (assoc :aggregate aggregate))))
                 ret))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn aggregator [aggregator-id]
  (registry/entity :aggregator aggregator-id))

(defn aggregator-by-command [command-name]
  (let [aggregator-id (registry/entity :aggregator-id-by-command command-name)]
    (aggregator aggregator-id)))

(defn aggregators []
  (registry/entities :aggregator))


(defn- update-aggregator [aggregator-id f]
  (registry/update-entity
   :aggregator aggregator-id
   #(f (or % (new-aggregator aggregator-id)))))


(defn reg-command-handler
  [aggregator-id command-name options f]
  (u/assert-spec ::aggregator-id aggregator-id)
  (u/assert-spec simple-keyword? command-name)
  (u/assert-spec ::handler-f f)
  (u/assert-spec ::handler-options options)
  (let [command-name (registry/as-global-keyword
                      command-name
                      (registry/bounded-context aggregator-id))
        handler {:aggregator-id aggregator-id
                 :command command-name
                 :f f
                 :options options}]
    (update-aggregator aggregator-id
                       #(add-command-handler % handler))
    (registry/register
     :aggregator-id-by-command command-name aggregator-id)
    handler))


(defmacro def-command
  [& args]
  (let [[command options f] (if (< (count args) 3)
                              [(nth args 0) {} (nth args 1)]
                              args)
        id (keyword (ns-name *ns*))]
    `(reg-command-handler ~id ~command ~options ~f)))


(defn reg-event-handler
  [aggregator-id event-name options f]
  (u/assert-spec ::aggregator-id aggregator-id)
  (u/assert-spec ::event-name event-name)
  (u/assert-spec ::handler-f f)
  (u/assert-spec ::handler-options options)
  (let [handler {:aggregator-id aggregator-id
                 :event event-name
                 :f f
                 :options options}]
    (update-aggregator aggregator-id
                       #(add-event-handler % handler))
    handler))

#_(macroexpand '(def-command :punch {} (fn [])))


(defmacro def-event
  [& args]
  (let [[event options f] (if (< (count args) 3)
                             [(nth args 0) {} (nth args 1)]
                             args)
        id (keyword (ns-name *ns*))]
    `(reg-event-handler ~id ~event ~options ~f)))


(defn reg-test-flow
  [aggregator-id flow-name options commands]
  (let [commands (map #(assoc % :command/name
                              (registry/as-global-keyword
                               (get % :command/name)
                               (registry/bounded-context aggregator-id)))
                      commands)]
    (registry/register :command-test-flow [aggregator-id flow-name]
                       (assoc options
                              :id [aggregator-id flow-name]
                              :aggregator aggregator-id
                              :name flow-name
                              :commands commands))))


(defmacro def-test-flow
  [& args]
  (let [[nam options f] (if (< (count args) 3)
                          [(nth args 0) {} (nth args 1)]
                          args)
        id (keyword (ns-name *ns*))]
    `(reg-test-flow ~id ~nam ~options ~f)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

