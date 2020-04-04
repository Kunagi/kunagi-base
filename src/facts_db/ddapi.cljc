(ns facts-db.ddapi
  (:require
   [clojure.spec.alpha :as s]

   [conform.api :refer [validate]]
   [facts-db.updating2 :as db-updating2]))


;;; spec

(s/def ::db (s/and map?
                   #(= ::identifier (get-in % [:db/config ::identifier]))))


(s/def ::api-def-id qualified-keyword?)
(s/def ::event-def-id qualified-keyword?)
(s/def ::query-def-id qualified-keyword?)

(s/def ::api-id simple-keyword?)

(s/def ::event-id simple-keyword?)
(s/def ::event-args map?)
(s/def ::event      (s/cat :id    ::event-id
                           :args  (s/? ::event-args)))
(s/def ::events (s/coll-of ::event))

(s/def ::query-id   simple-keyword?)
(s/def ::query-arg any?)
(s/def ::query (s/cat ::query-id (s/* ::query-arg)))

(s/def ::api-definition (s/keys))

(s/def ::event-handler fn?)

(s/def ::query-handler fn?)


;;;


(defmulti apply-event (fn [db event args] event))

(defmulti run-query (fn [db query args] query))

(defmulti create-db (fn [api args] api))


;;; client api

(defn events>
  [db events]
  (let [events (remove nil? events)]
    (validate ::events>
              [:map db ::db]
              [:val events ::events])
    (reduce
     (fn [db [event-name args]]
       (let [api-ns (get-in db [:db/config :db/api-ns])
             event-id (keyword (name api-ns)
                               (if (keyword? event-name)
                                 (name event-name)
                                 event-name))
             change-request (apply-event db event-id args)]
         (db-updating2/update-facts db change-request)))
     db
     events)))


(defn <query
  [db query]
  (validate ::<query
            [:map  db    ::db
             :val  query ::query])
  (let [[query-name & query-args] query
        api-ns (get-in db [:db/config :db/api-ns])
        query-id (keyword (name api-ns)
                          (if (keyword? query-name)
                            (name query-name)
                            query-name))]
    (run-query db query-id query-args)))


(defn new-db
  [api args]
  (create-db api args))


;;; implementor api


(def !apis (atom {}))
(def !api-ns->api-id (atom {}))


(defn def-api
  [api-def-id & {:as api :keys [db-constructor
                                db-instance-identifier-args-key]}]
  (validate ::def-api
            [:val api-def-id ::api-def-id]
            [:val api ::api-definition])
  (let [api-id (keyword (name api-def-id))
        api-ns (keyword (namespace api-def-id))
        api (assoc api :id api-id
                       :ns api-ns)]
    (defmethod create-db api-id [api args]
      (cond-> (db-updating2/new-db)

        true
        (assoc-in [:db/config ::identifier] ::identifier)

        true
        (assoc-in [:db/config :db/api-ns] api-ns)

        db-constructor
        (db-updating2/update-facts (db-constructor args))))

        ;; true
        ;; (assoc-in [:db/config :events>] events>)

        ;; true
        ;; (assoc-in [:db/config :<query] <query)))
    (swap! !apis assoc api-id api)
    (swap! !api-ns->api-id assoc api-ns api-id)))


(defn def-event
  [event-id event-handler]
  (validate ::def-event
            [:val event-id ::event-def-id]
            [:val event-handler ::event-handler])
  (let [event {:id event-id
               :handler event-handler}
        api-ns (keyword (namespace event-id))
        api-id (@!api-ns->api-id api-ns)]
    (when-not api-id (throw (ex-info (str "No API for namespace " api-ns)
                                     {:missing-ns api-ns
                                      :available-nss (keys @!api-ns->api-id)})))
    (defmethod apply-event event-id [db event-id args]
      (event-handler db args))
    (swap! !apis assoc-in [api-id :events event-id] event)))


(defn def-query
  [query-id query-handler]
  (validate ::def-query
            [:val query-id ::query-def-id]
            [:val query-handler ::query-handler])
  (let [query {:id query-id
               :handler query-handler}
        api-ns (keyword (namespace query-id))
        api-id (@!api-ns->api-id api-ns)]
    (when-not api-id (throw (ex-info (str "No API for namespace " api-ns)
                                     {:missing-ns api-ns
                                      :available-nss (keys @!api-ns->api-id)})))
    (defmethod run-query query-id [db query-id args]
      (apply query-handler (into [db] args)))
    (swap! !apis assoc-in [api-id :queries query-id] query)))



;;; integrator api


(defn defined-apis
  []
  (-> @!apis vals))
