(ns kcu.aggregator
  #?(:cljs (:require-macros [kcu.aggregator]))
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]
   [kcu.projector :as projector]))


(s/def ::aggregator-id simple-keyword?)

(s/def ::handler-f fn?)
(s/def ::handler-options map?)

(s/def ::command-name simple-keyword?)
(s/def ::command-args map?)
(s/def ::command (s/cat :name ::command-name :args ::command-args))


(defn- add-command-handler
  [aggregator handler]
  (-> aggregator
      (assoc-in [:command-handlers (-> handler :command)] handler)))


(defn- new-aggregator [id]
  {:id id
   :command-handlers {}})

(defn- new-registry []
  {:aggregators {}})

(defn- update-aggregator [registry aggregator-id f]
  (let [aggregator (or (get-in registry [:aggregators aggregator-id])
                       (new-aggregator aggregator-id))]
    (assoc-in registry [:aggregators aggregator-id] (f aggregator))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn new-aggregate
  [aggregator]
  (let [aggregator-id (-> aggregator :id)]
    {:aggregate/aggregator aggregator-id}))


(defn- register-input [inputs input-type input-args]
  {:input-type input-type
   :input-args input-args}
  (let [infos (or (get inputs input-type)
                  #{})
        infos (conj infos input-args)]
    (assoc inputs input-type infos)))


(defn- as-projection-ref [projection-ref]
  (cond
    (vector? projection-ref) projection-ref
    (seq? projection-ref) (into [] projection-ref)
    (keyword? projection-ref) [projection-ref]
    :else (throw (ex-info (str "Invalid projection-ref `" projection-ref "`.")
                          {:invalid-projection-ref projection-ref}))))


(defn- provide-projection
  [aggregator aggregate projection-ref]
  (let [projection-ref (as-projection-ref projection-ref)]
    (get-in aggregate [:projection projection-ref])))


(defn- context-f [aggregator aggregate !inputs]
  (fn context [input-type & input-args]
    (swap! !inputs #(conj % (if input-args
                              [input-type (into [] input-args)]
                              input-type)))
    (case input-type
      :projection (provide-projection aggregator aggregate input-args)
      :timestamp (u/current-time-millis)
      (throw (ex-info (str "Unsupported context input `" input-type "`.")
                      {:input-type input-type
                       :input-args input-args})))))


(defn- apply-command
  [aggregator aggregate command]
  (u/assert-spec ::command command)
  (let [[command-name command-args] command
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
        context-f (context-f aggregator aggregate !inputs)
        effects (try
                  (f context-f command-args)
                  (catch #?(:clj Exception :cljs :default) ex
                     (throw (ex-info (str "Executing command `"
                                      command-name
                                      "` with aggregator `"
                                      aggregator-id
                                      "` failed. Commnad handler crashed.")
                                     {:aggregator-id aggregator-id
                                      :command command}
                                     ex))))]
      (try
        (u/assert-entity effects {} {:events map?})
        (catch #?(:clj Exception :cljs :default) ex
          (throw (ex-info (str "Executing command `"
                               command-name
                               "` with aggregator `"
                               aggregator-id
                               "` failed. Command handler returned invalid effects. ")
                          {:aggregator-id aggregator-id
                           :command command
                           :invalid-effects effects}
                          ex))))
      {:effects effects
       :inputs @!inputs}))


(defn- apply-events [aggregator aggregate projection-ref events]
  (let [projection-ref (as-projection-ref projection-ref)
        projector-id (first projection-ref)
        projector (projector/projector projector-id)
        projection-path [:projections projection-ref]
        projection (get-in aggregate projection-path)
        projection-ret (projector/project projector projection events)
        projection (get projection-ret :projection)]
    (-> aggregate
        (assoc-in projection-path projection)
        (assoc-in [:projection-results projection-ref] projection-ret))))


(defn- apply-events-map
  [aggregator aggregate events-map]
  (reduce (fn [aggregate [projection-ref events]]
            (apply-events aggregator aggregate projection-ref events))
          aggregate
          events-map))


(defn simulate-commands
  [aggregator commands]
  (let [ret {:aggregator aggregator
             :commands commands
             :aggregate (new-aggregate aggregator)
             :flow []}]
    (->> commands
         (reduce (fn [ret command]
                   (let [aggregate (get ret :aggregate)

                         [result command-exception]
                         (try
                           [(apply-command aggregator aggregate command)]
                           (catch #?(:clj Exception :cljs :default) ex
                             [{} ex]))

                         effects (get result :effects)
                         events (get effects :events)

                         [aggregate projection-exception]
                         (try
                           [(apply-events-map aggregator aggregate events)]
                           (catch #?(:clj Exception :cljs :default) ex
                             [aggregate ex]))

                         projection-results (get aggregate :projection-results)
                         aggregate (dissoc aggregate :projection-results)

                         flow (-> ret :flow)
                         step {:command command
                               :inputs (get result :inputs)
                               :effects effects
                               :index (count flow)
                               :aggregate aggregate
                               :projection-results projection-results
                               :command-exception command-exception
                               :projection-exception projection-exception}
                         flow (conj flow step)]
                     (-> ret
                         (assoc :flow flow)
                         (assoc :aggregate aggregate))))
                 ret))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce !registry
  (atom (new-registry)))

(defn aggregator [aggregator-id]
  (if-let [aggregator (get-in @!registry [:aggregators aggregator-id])]
    aggregator
    (throw (ex-info (str "Missing aggregator `" aggregator-id "`. "
                         "Existing: `" (-> @!registry :aggregators keys) "`.")
                    {:invalid-aggregator-id aggregator-id}))))


(defn reg-command-handler
  [aggregator-id command-name options f]
  (u/assert-spec ::aggregator-id aggregator-id)
  (u/assert-spec ::command-name command-name)
  (u/assert-spec ::handler-f f)
  (u/assert-spec ::handler-options options)
  (let [handler {:aggregator-id aggregator-id
                 :command command-name
                 :f f
                 :options options}]
    (swap! !registry
           (fn [registry]
             (update-aggregator registry
                               aggregator-id
                               #(add-command-handler % handler))))
    handler))


(defmacro def-command
  [& args]
  (let [[command options f] (if (< (count args) 3)
                              [(nth args 0) {} (nth args 1)]
                              args)
        id (keyword (ns-name *ns*))]
    `(reg-command-handler
      ~id
      ~command
      ~options
      ~f)))

#_(macroexpand '(def-command :punch {} (fn [])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

