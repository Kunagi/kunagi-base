(ns kcu.reframe
  #?(:cljs (:require-macros [kcu.reframe]))
  (:require
   [re-frame.core :as rf]

   [kcu.utils :as u]))

;; TODO :durable? with localStorage

;;; init-fns


(defonce !init-fs (atom {}))


(defn reg-init-f
  [id f]
  (swap! !init-fs assoc id f))


(defn init []
  (rf/dispatch-sync [::init]))


(rf/reg-event-db
 ::init
 (fn [db]
   (reduce (fn [db [id init-f]]
             (try
               (init-f db)
               (catch :default ex
                 (tap> [:err ::init-f-failed {:id id :init-f init-f :ex ex}])
                 db)))
           db @!init-fs)))


;;; lenses


(defn lense-full-path
  "Full path to `lense` value in `app-db`."
  [lense]
  (if-let [parent (-> lense :parent)]
    (conj (lense-full-path parent) (-> lense :key))
    [(-> lense :key)]))


(defn reg-lense [lense]
  (try
    (let [id (u/getm lense :id)
          k (u/getm lense :key)
          parent (get lense :parent)]

      (if parent
       (rf/reg-sub
        id
        (fn [] (rf/subscribe [(-> parent :id)]))
        (fn [db] (get db k)))
       (rf/reg-sub
        id
        (fn [db] (-> db (get k)))))

      lense)
    (catch #?(:clj Exception :cljs :default) ex
      (throw (ex-info (str "Creating lense `" (-> lense :id) "` failed.")
                      {:lense lense
                       :cause ex}
                      ex)))))


(defmacro def-lense
  [sym lense]
  (let [k (keyword (str sym))
        id (keyword (str (ns-name *ns*)) (str sym))]
    `(defonce ~sym (reg-lense (merge {:id ~id
                                      :key ~k}
                                     ~lense)))))


(defn subscribe
  [lense]
  (if-let [subscription (rf/subscribe [(-> lense :id)])]
    @subscription
    nil))


(defn lense-get
  [db lense]
  (get-in db (lense-full-path lense)))


(defn dispatch-setter [lense new-value]
  (rf/dispatch [::lense-set lense new-value]))


(rf/reg-event-db
  ::lense-set
  (fn [db [_ lense new-value]]
    (assoc-in db (lense-full-path lense) new-value)))
