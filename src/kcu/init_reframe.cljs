(ns kcu.init-reframe
  (:require
   [re-frame.core :as rf]

   [kcu.init :as init]))


(reset! init/app-db-type :re-frame)


(rf/reg-sub
 :app/db
 (fn [db _]
   db))


(rf/reg-sub
 :app/model
 (fn [_ _]
   @app-config/!app-model))



;; (rf/reg-event-fx
;;  ::transact
;;  (fn [context [_ f]]
;;    (let [context (f context)
;;          effects (-> context :effects)]
;;      (assoc effects :db (-> context :db)))))


;; (defn transact [f _transaction-boundary]
;;   (rf/dispatch [::transact f]))


;; (reset! app-config/!transact transact)
