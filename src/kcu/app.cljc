(ns kcu.app
  (:require
   [clojure.spec.alpha :as s]

   [kcu.init :as init]
   [kcu.app-model :as model]))


(defonce !app-model (model/new-appmodel))


(defn reg-model
  [n sym type attrs]
  (let [id (keyword (str (ns-name n)) (str (name type) "." sym))
        _ (tap> [:dbg ::reg-model id])
        model (assoc attrs :id id
                     :type type)]
    (try
      (swap! !app-model model/add-model model)
      (catch #?(:clj Exception :cljs :default) ex
        (throw (ex-info (str "reg-model failed for model `" id "`")
                        {:model model}
                        ex))))
    id))


(defn reg-model-type
  [type]
  (swap! !app-model model/add-type type))



;;; transactions


;; (reg-model-type
;;  {:id :app/transaction-boundary})


;; (defmacro def-transaction-boundary
;;   [sym attrs]
;;   (let [id (reg-model *ns* sym :app/transaction-boundary (eval attrs))]
;;     `(def ~sym ~id)))


;; (def-transaction-boundary app-db {})


;; (defn transact [f transaction-boundary-ref]
;;   (let [boundary-id (if transaction-boundary-ref
;;                       (if (qualifed-keyword? transaction-boundary-ref)
;;                         transaction-boundary-ref
;;                         (first transaction-boundary-ref))
;;                       db)
;;         entity-id (when (vector? transaction-boundary-ref) (second transaction-boundary-ref))
;;         boundary (model/type @app-config/!app-model boundary-id)
;;         boundary (assoc boundary :entity-id entity-id)]
;;     (@app-config/transact f boundary)))



;;; events


(reg-model-type
 {:id :app/event})


(defmacro def-event
  [sym attrs]
  (let [id (reg-model *ns* sym :app/event attrs)]
    `(def ~sym ~id)))


(reg-model-type
 {:id :app/event-handler
  :attrs [{:k :event
           :mandatory? true
           :spec ::model/model-id
           :index? true}]})


(defmacro def-event-handler
  [sym attrs]
  (let [id (reg-model *ns* sym :app/event-handler (eval attrs))]
    `(def ~sym ~id)))


(defn dispatch!
  ([event]
   (dispatch! event nil))
  ([event context]
   (@init/dispatch event context)))
