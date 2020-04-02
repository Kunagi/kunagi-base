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
   :bounded-context (registry/bounded-context id)
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn new-projection-pool
  [projector load-f store-f]
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
                (doseq [projection (-> @!projections vals)]
                  (store-f projector (-> projection :projection/id) projection))))
     :projections (fn [] (vals @!projections))}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn handler-projection-id-resolver [projector handler]
  (if (get projector :singleton?)
    (constantly true)
    (or (-> handler :options :id-resolver)
        (-> projector :id-resolver))))


(defn projection-id [projector handler event]
  (assert-projector projector)
  (u/assert-entity {:event/name ::event-name})
  (let [projector-id (-> projector :id)
        id-resolver (handler-projection-id-resolver projector handler)
        _ (when-not id-resolver
            (throw (ex-info (str "Projecting event `"
                                 (-> event :event/name)
                                 "` with projector `"
                                 projector-id
                                 "` failed. Missing `:id-resolver` "
                                 "or `singleton? true`.")
                            {:projector-id projector-id
                             :event event})))
        entity-id (when id-resolver (id-resolver event))
        _ (when-not (or entity-id (-> projector :singleton?))
            (throw (ex-info (str "Projecting event `"
                                 (-> event :event/name)
                                 "` with projector `"
                                 projector-id
                                 "` failed. `id-resolver` provided no id.")
                            {:projector-id projector-id
                             :event event})))]
    entity-id))


(defn apply-event [projector handler projection-id projection event]
  (let [event-name (-> event :event/name)
        projector-id (-> projector :id)
        f (get handler :f)
        _ (tap> [::!!! ::projection projection])
        projection (or projection (new-projection projector projection-id))
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
        (update :projection/handled-events conj (-> event :event/id)))))


(defn handler-for-event [projector event]
  (assert-projector projector)
  (let [event-name (-> event :event/name)]
    (get-in projector [:handlers event-name])))


(defn handle-event [projector get-projection event]
  (assert-projector projector)
  (when-let [handler (handler-for-event projector event)]
    (let [entity-id (projection-id projector handler event)
          projection (get-projection entity-id)]
      (apply-event projector handler entity-id projection event))))


(defn handle-events [projector p-pool events]
  (let [get-f (-> p-pool :get)
        update-f (-> p-pool :update)
        flush-f (-> p-pool :flush)]
    (doseq [event events]
      (when-let [projection (handle-event projector get-f event)]
        (update-f (-> projection :projection/id) projection)))
    (flush-f)
    ((-> p-pool :projections))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn projector [projector-id]
  (registry/entity :projector projector-id))


(defn projectors []
  (registry/entities :projector))


(defn projectors-by-type [projector-type]
  (registry/entities-by :projector :type projector-type))


(defn projectors-by-event [event-name]
  (map projector (registry/entity :projector-ids-by-event event-name)))


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
    (registry/update-entity
     :projector-ids-by-event
     (registry/as-global-keyword event-name (registry/bounded-context projector-id))
     (fn [ids]
       (conj (or ids #{}) projector-id)))
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


