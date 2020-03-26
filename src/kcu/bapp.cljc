(ns kcu.bapp
  (:refer-clojure :exclude [read])
  #?(:cljs (:require-macros [kcu.bapp]))
  (:require
   [re-frame.core :as rf]
   [ajax.core :as ajax]

   [kcu.utils :as u]))

;; FIXME updates on parent lenses must be prevented
;; FIXME updates on child lenses with durable parents
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


(defn create-default-value! [lense]
  (when-let [default-value (-> lense :default-value)]
    (let [default-value (if (fn? default-value)
                          (default-value)
                          default-value)]
      (when (-> lense :durable?)
        (store! lense default-value))
      default-value)))


(defn- load! [lense]
  (let [value #?(:cljs (-> js/window
                           .-localStorage
                           (.getItem (storage-key lense))
                           u/decode-edn)
                 :clj (throw (ex-info "load! not implemented" {:lense lense})))
        value (or value (create-default-value! lense))]
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
                    (or (get-in db path)
                        (create-default-value! lense)))
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
    `(def ~sym (reg-lense (merge {:id ~id
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



;;; default lenses


(def bapp
  (reg-lense {:id ::bapp
              :key :bapp}))



;;; errors

(def errors
  (reg-lense {:id ::errors
              :key :errors
              :parent bapp}))


;;; anti-forgery


(defonce !anti-forgery-token (atom nil))

(defn POST
  [endpoint options]
  ;; FIXME re-request token if POST response contains "invalid anti-forgery token"
  (if-let [anti-forgery-token @!anti-forgery-token]
    (ajax/POST
     endpoint
     (-> options
         (assoc-in [:headers "X-CSRF-Token"] anti-forgery-token)))
    (ajax/GET
     "/api/anti-forgery-token"
     {:handler (fn [token]
                 (tap> [:!!! ::anti-forgery-token token])
                 (reset! !anti-forgery-token token)
                 (POST endpoint options))})))

;;; conversation


(def conversation
  (reg-lense {:id ::conversation
              :key :conversation
              :parent bapp}))


(def conversation-id
  (reg-lense {:id ::conversation-id
              :key :id
              :parent conversation
              :durable? true
              :default-value u/random-uuid-string}))


(defn transmit-messages-to-server!
  [db messages]
  (POST
   "/api/conversation"
   {
    :format :text
    :params {:conversation (read db conversation-id)
             :messages messages}
    ;; :body (u/encode-edn {:conversation (read db conversation-id)
    ;;                      :messages messages})
    :handler (fn [response]
               (tap> [:dbg ::messages-delivered-to-server
                      {:response response
                       :messages messages}]))})
  db)

(reg-init-f
 ::continue-conversation
 (fn [db]
   (transmit-messages-to-server! db [[:conversation/continue]])
   db))


;; (defn fetch-messages-from-server
;;   [])