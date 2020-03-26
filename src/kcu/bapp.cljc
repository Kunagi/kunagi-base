(ns kcu.bapp
  (:refer-clojure :exclude [read])
  #?(:cljs (:require-macros [kcu.bapp]))
  (:require
   [re-frame.core :as rf]

   [kcu.utils :as u]))

;; TODO spec and validation for lenses
;; TODO spec for lense values

;;; init-fns


(defonce !init-fs (atom {}))


(defn reg-init-f
  [id f]
  (swap! !init-fs assoc id f))


(defn init! []
  (rf/dispatch-sync [::init]))


(rf/reg-event-db
 ::init
 (fn [db]
   (reduce (fn [db [id init-f]]
             (try
               (init-f db)
               (catch #?(:clj Exception :cljs :default) ex
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


(defn- storage-key [lense]
  (or (-> lense :storage-key)
      (-> lense :id)))


(defn- store! [lense value]
  (tap> [:dbg ::store! {:lense (-> lense :id)
                        :value value}])
  #?(:cljs (-> js/window
               .-localStorage
               (.setItem (storage-key lense) (u/encode-edn value)))
     :clj (throw (ex-info "store! not implemented" {:lense lense :value value}))))


(defonce !loaded-lenses (atom #{}))

(defn- loaded? [lense]
  (contains? @!loaded-lenses (-> lense :id)))


(defn- load! [lense]
  (let [value #?(:cljs (-> js/window
                           .-localStorage
                           (.getItem (storage-key lense))
                           u/decode-edn)
                 :clj (throw (ex-info "load! not implemented" {:lense lense})))]
    (swap! !loaded-lenses conj (-> lense :id))
    (tap> [:dbg ::load! {:lense (-> lense :id)
                         :value value}])
    value))


(defn read
  [db lense]
  (when (and (-> lense :durable?)
             (not (loaded? lense)))
    (throw (ex-info (str "Durable lense `"
                         (-> lense :id)
                         "` not loaded. Read operation prevented.")
                    {:lense lense})))
  (get-in db (lense-full-path lense)))


(defn swap
  [db lense f & args]
  (let [durable? (-> lense :durable?)
        path (lense-full-path lense)
        old-value (if (and durable?
                           (not (loaded? lense)))
                    (load! lense)
                    (get-in db path))
        new-value (apply f old-value args)]
    (when (and durable? (not= old-value new-value))
      (store! lense new-value))
    (assoc-in db path new-value)))


(defn reset
  [db lense value]
  (swap db lense (constantly value)))


(defn subscribe
  [lense]
  @(rf/subscribe [::read lense]))


(defn dispatch-reset [lense new-value]
  (rf/dispatch [::reset lense new-value]))


(defn reg-lense [lense]
  (try
    (let [id (u/getm lense :id)
          k (u/getm lense :key)
          parent (get lense :parent)
          durable? (get lense :durable?)
          auto-load? (get lense :auto-load? true)]

      (when (and durable? auto-load?)
        (reg-init-f [::load id]
                    (fn [db]
                      (assoc-in db (lense-full-path lense) (load! lense)))))

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


(rf/reg-event-db
  ::reset
  (fn [db [_ lense new-value]]
    (reset db lense new-value)))


;; TODO optimize: recursive input signals
(rf/reg-sub
 ::read
 (fn [db [_ lense]]
   (read db lense)))
