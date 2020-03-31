(ns kcu.projector
  #?(:cljs (:require-macros [kcu.projector]))
  (:require
   [clojure.spec.alpha :as s]

   [kcu.registry :as registry]
   [kcu.utils :as u]))


(s/def ::projector-id simple-keyword?)

(s/def ::handler-f fn?)
(s/def ::handler-options map?)

(s/def ::event-name keyword?)


(defn- add-event-handler
  [projector handler]
  (-> projector
      (assoc-in [:handlers (-> handler :event)] handler)))


(defn- new-projector [id]
  {:id id
   :handlers {}})


(defn- new-registry []
  {:projectors {}})




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn assert-projector [projector]
  (u/assert-entity projector {:id ::projector-id
                              :handlers map?}))

(defn assert-projection [projection]
  (u/assert-entity projection {:projection/projector ::projector-id}))


(defn new-projection
  [projector entity-id]
  (assert-projector projector)
  (let [projector-id (-> projector :id)
        projection-id (if entity-id
                        entity-id
                        (name projector-id))]
    {:projection/projector projector-id
     :projection/handled-events #{}
     :projection/type (-> projector :type)
     :projection/id projection-id}))


;; (defn as-projection-ref
;;   ([projector-id entity-id]
;;    (if entity-id
;;      [projector-id entity-id]
;;      [projector-id]))
;;   ([projection-ref]
;;    (cond
;;      (vector? projection-ref) (if (second projection-ref)
;;                                 projection-ref
;;                                 [(first projection-ref)])
;;      (seq? projection-ref) (into [] projection-ref)
;;      (keyword? projection-ref) [projection-ref]
;;      :else (throw (ex-info (str "Invalid projection-ref `" projection-ref "`.")
;;                            {:invalid-projection-ref projection-ref})))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn new-projection-pool [projector load-f store-f]
  (let [!projections (atom {})]
    {:get (fn [entity-id]
            (or
             (get @!projections [(-> projector :id) entity-id])
             (when load-f (load-f projector entity-id))
             (new-projection projector entity-id)))
     :update (fn [entity-id projection]
               (swap! !projections
                      assoc [(-> projector :id) entity-id]
                      projection))
     :flush (fn []
              (when store-f
                (doseq [projection @!projections]
                  (store-f projector (-> projection :projection/id) projection))))
     :projections (fn [] (vals @!projections))}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; (defn projection-ref
;;   [projection]
;;   (assert-projection projection)
;;   (let [projector-id (get projection :projection/projector)
;;         entity-id (get projection :projection/entity-id)]
;;     (if entity-id
;;       [])))

;; (defn- apply-event
;;   [projector projection event]
;;   (assert-projector projector)
;;   (assert-projection projection)
;;   (let [[event-name event-args] event
;;         projector-id (-> projector :id)
;;         handler (get-in projector [:handlers event-name])
;;         _ (when-not handler
;;             (throw (ex-info (str "Projecting event `"
;;                                  event-name
;;                                  "` with projector `"
;;                                  projector-id
;;                                  "` failed. No handler for event.")
;;                             {:projector-id projector-id
;;                              :unknown-event event
;;                              :known-events (-> projector :handlers keys)
;;                              :projection projection})))
;;         f (get handler :f)
;;         projection (try
;;                      (f projection event-args)
;;                      (catch #?(:clj Exception :cljs :default) ex
;;                         (throw (ex-info (str "Projecting event `"
;;                                          event-name
;;                                          "` with projector `"
;;                                          projector-id
;;                                          "` failed. Event handler crashed.")
;;                                         {:projector-id projector-id
;;                                          :event event
;;                                          :projection projection}
;;                                         ex))))]
;;       (try
;;         (u/assert-entity projection {:projection/projector ::projector-id})
;;         (catch #?(:clj Exception :cljs :default) ex
;;           (throw (ex-info (str "Projecting event `"
;;                                event-name
;;                                "` with projector `"
;;                                projector-id
;;                                "` failed. Event handler returned invalid projection. ")
;;                           {:projector-id projector-id
;;                            :event event
;;                            :invalid-projection projection}
;;                           ex))))
;;       projection))


(defn handler-projection-id-resolver [projector handler event]
  (or (-> handler :options :id-resolver)
      (-> projector :id-resolver)))


(defn- handle-event [projector get-projection event]
  (assert-projector projector)
  (let [event-name (-> event :event/name)
        handler (get-in projector [:handlers event-name])]
    (when handler
      (let [projector-id (-> projector :id)
            id-resolver (handler-projection-id-resolver projector handler event)
            entity-id (when id-resolver (id-resolver event))
            projection (get-projection entity-id)
            f (get handler :f)
            projection (try
                         (f projection event)
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
        (-> projection
            (update :projection/handled-events conj (-> event :event/id)))))))


(defn handle-events [projector p-pool events]
  (let [get-f (-> p-pool :get)
        update-f (-> p-pool :update)
        flush-f (-> p-pool :flush)]
    (doseq [event events]
      (when-let [projection (handle-event projector get-f event)]
        (update-f (-> projection :projection/id) projection)))
    (flush-f)
    ((-> p-pool :projections))))



;; (defn project
;;   [projector projection events]
;;   (assert-projector projector)
;;   (assert-projection projection)
;;   (let [ret {:projector projector
;;              :events events
;;              :projection projection
;;              :flow []}]
;;     (->> events
;;          (reduce (fn [ret event]
;;                    (let [projection (-> ret :projection)
;;                          projection (apply-event projector projection event)
;;                          flow (-> ret :flow)
;;                          step {:event event
;;                                :projection projection
;;                                :index (count flow)}
;;                          flow (conj flow step)]
;;                      (-> ret
;;                          (assoc :flow flow)
;;                          (assoc :projection projection))))
;;                  ret))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn projector [projector-id]
  (registry/entity :projector projector-id))


(defn projectors []
  (registry/entities :projector))


(defn projectors-by-type [projector-type]
  (registry/entities-by :projector :type projector-type))


;; (defn projector [projector-id]
;;   (if-let [projector (get-in @!registry [:projectors projector-id])]
;;     projector
;;     (throw (ex-info (str "Missing projector `" projector-id "`. "
;;                          "Existing: `" (-> @!registry :projectors keys) "`.")
;;                     {:invalid-projector-id projector-id}))))


(defn- update-projector [projector-id f]
  (registry/update-entity
   :projector projector-id
   #(f (or % (new-projector projector-id)))))


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
    (update-projector projector-id
                      #(add-event-handler % handler))
    handler))


(defn configure-projector
  [projector-id options]
  (update-projector projector-id #(merge % options)))


(defmacro configure [options]
  (let [id (keyword (ns-name *ns*))]
    `(configure-projector ~id ~options)))


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
