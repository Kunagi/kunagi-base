(ns kcu.aggregator
  #?(:cljs (:require-macros [kcu.aggregator]))
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]))

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


(defn- provide-projection
  [aggregator aggregate projection-ref]
  (let [projection-ref (if (vector? projection-ref)
                         projection-ref
                         [projection-ref])]
    {}))

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
        f (get handler :f)
        !inputs (atom [])
        context-f (context-f aggregator aggregate !inputs)
        effects (try
                  (f context-f command-args)
                  (catch #?(:clj Exception :cljs :default) ex
                     (throw (ex-info (str "Executing command `"
                                      command-name
                                      "` eith aggregator `"
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


(defn simulate-commands
  [aggregator commands]
  (let [aggregate (new-aggregate aggregator)
        ret {:aggregator aggregator
             :commands commands
             :flow []}]
    (->> commands
         (reduce (fn [ret command]
                   (let [result (apply-command aggregator aggregate command)
                         effects (get result :effects)
                         events (-> effects :events)
                         flow (-> ret :flow)
                         step {:command command
                               :inputs (get result :inputs)
                               :effects effects
                               :index (count flow)}
                         flow (conj flow step)]
                     (-> ret
                         (assoc :flow flow))))
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

