(ns kcu.projector
  #?(:cljs (:require-macros [kcu.projector]))
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]))

(s/def ::projector-id simple-keyword?)

(s/def ::handler-f fn?)
(s/def ::handler-options map?)

(s/def ::event-name simple-keyword?)
(s/def ::event-args map?)
(s/def ::event (s/cat :name ::event-name :args ::event-args))


(defn- add-event-handler
  [projector handler]
  (-> projector
      (assoc-in [:handlers (-> handler :event)] handler)))


(defn- new-projector [id]
  {:id id
   :handlers {}})


(defn- new-registry []
  {:projectors {}})


(defn- update-projector [registry projector-id f]
  (let [projector (or (get-in registry [:projectors projector-id])
                      (new-projector projector-id))]
    (assoc-in registry [:projectors projector-id] (f projector))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assert-projector [projector]
  (u/assert-entity projector {:id ::projector-id
                              :handlers map?}))

(defn assert-projection [projection]
  (u/assert-entity projection {:projection/projector ::projector-id}))


(defn new-projection
  [projector entity-id]
  (assert-projector projector)
  (let [projector-id (-> projector :id)]
    (if entity-id
      {:projection/projector projector-id
       :projection/entity-id entity-id}
      {:projection/projector projector-id})))


(defn as-projection-ref
  ([projector-id entity-id]
   (if entity-id
     [projector-id entity-id]
     [projector-id]))
  ([projection-ref]
   (cond
     (vector? projection-ref) (if (second projection-ref)
                                projection-ref
                                [(first projection-ref)])
     (seq? projection-ref) (into [] projection-ref)
     (keyword? projection-ref) [projection-ref]
     :else (throw (ex-info (str "Invalid projection-ref `" projection-ref "`.")
                           {:invalid-projection-ref projection-ref})))))


;; (defn projection-ref
;;   [projection]
;;   (assert-projection projection)
;;   (let [projector-id (get projection :projection/projector)
;;         entity-id (get projection :projection/entity-id)]
;;     (if entity-id
;;       [])))

(defn apply-event
  [projector projection event]
  (assert-projector projector)
  (assert-projection projection)
  (u/assert-spec ::event event)
  (let [[event-name event-args] event
        projector-id (-> projector :id)
        handler (get-in projector [:handlers event-name])
        _ (when-not handler
            (throw (ex-info (str "Projecting event `"
                                 event-name
                                 "` with projector `"
                                 projector-id
                                 "` failed. No handler for event.")
                            {:projector-id projector-id
                             :unknown-event event
                             :known-events (-> projector :handlers keys)
                             :projection projection})))
        f (get handler :f)
        projection (try
                     (f projection event-args)
                     (catch #?(:clj Exception :cljs :default) ex
                        (throw (ex-info (str "Projecting event `"
                                         event-name
                                         "` with projector `"
                                         projector-id
                                         "` failed. Event handler crashed.")
                                        {:projector-id projector-id
                                         :event event
                                         :projection projection}
                                        ex))))]
      (try
        (u/assert-entity projection {:projection/projector ::projector-id})
        (catch #?(:clj Exception :cljs :default) ex
          (throw (ex-info (str "Projecting event `"
                               event-name
                               "` with projector `"
                               projector-id
                               "` failed. Event handler returned invalid projection. ")
                          {:projector-id projector-id
                           :event event
                           :invalid-projection projection}
                          ex))))
      projection))


(defn project
  [projector projection events]
  (assert-projector projector)
  (assert-projection projection)
  (let [ret {:projector projector
             :events events
             :projection projection
             :flow []}]
    (->> events
         (reduce (fn [ret event]
                   (let [projection (-> ret :projection)
                         projection (apply-event projector projection event)
                         flow (-> ret :flow)
                         step {:event event
                               :projection projection
                               :index (count flow)}
                         flow (conj flow step)]
                     (-> ret
                         (assoc :flow flow)
                         (assoc :projection projection))))
                 ret))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defonce !registry
  (atom (new-registry)))


(defn projector [projector-id]
  (if-let [projector (get-in @!registry [:projectors projector-id])]
    projector
    (throw (ex-info (str "Missing projector `" projector-id "`. "
                         "Existing: `" (-> @!registry :projectors keys) "`.")
                    {:invalid-projector-id projector-id}))))


(defn reg-event-handler
  [projector-id event-name options f]
  (u/assert-spec ::projector-id projector-id)
  (u/assert-spec ::event-name event-name)
  (u/assert-spec ::handler-f f)
  (u/assert-spec ::handler-options options)
  (let [handler {:projector-id projector-id
                 :event event-name
                 :f f
                 :options options}]
    (swap! !registry
           (fn [registry]
             (update-projector registry
                               projector-id
                               #(add-event-handler % handler))))))

(defmacro def-event
  [& args]
  (let [[event options f] (if (< (count args) 3)
                            [(nth args 0) {} (nth args 1)]
                            args)
        id (keyword (ns-name *ns*))]
    `(reg-event-handler
      ~id
      ~event
      ~options
      ~f)))


#?(:cljs (js/console.log (macroexpand-1 '(def-event :my-event (fn [])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (macroexpand
;;  '(def-event :punched {} (fn [])))

;; (def-event :punched {} (fn [])))
