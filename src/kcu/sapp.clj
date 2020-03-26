(ns kcu.sapp
  "Server Application"
  (:require
   [ring.util.request :as ring-request]
   [kcu.utils :as u]
   [kcu.txa :as txa]))


(def data-dir "app-data")


;;; conversations


(def conversations-dir (str data-dir "/conversations"))

(defonce !conversations-txas (atom {}))


(defn conversation-txa [conversation-id]
  (locking !conversations-txas
    (if-let [txa (get @!conversations-txas conversation-id)]
      txa
      (let [txa (txa/new-txa (str "conversation/" conversation-id)
                             {:file (str conversations-dir "/" conversation-id ".edn")})]
        (swap! !conversations-txas assoc conversation-id txa)
        txa))))


(defn http-serve-conversation
  [context]
  (let [params (-> context :http/request ring-request/body-string u/decode-edn)
        conversation-id (-> params :conversation)
        messages (-> params :messages)
        txa (conversation-txa conversation-id)]
    (txa/transact
     txa
     (fn [conversation]
       (let [conversation (or conversation
                              {:id conversation-id
                               :creation-time (u/current-time-millis)})]
         (-> conversation
             (assoc :last-activity-time (u/current-time-millis))))))))



;;; queries


(defonce !responders (atom {}))


(defn responder
  [id]
  (u/getm @!responders id))


(defn reg-responder
  [id options]
  (let [responder (assoc options :id id)]
    (swap! !responders assoc id responder)
    responder))


(defmacro def-responder
  [sym options]
  (let [id (keyword (str (ns-name *ns*)) (str sym))]
    `(def ~sym (reg-responder ~id ~options))))


(defn query-sync
  [responder query-args context]
  (tap> [:dbg ::query {:query (-> responder :id)
                       :args query-args}])
  (let [f (u/getm responder :f)
        response (f context query-args)]
    response))


(defn query-async
  [responder query-args context callback]
  (future
    (callback (query-sync responder query-args context))))


(defn http-serve-query [context]
  ;; TODO respond with error on error response
  (let [params (-> context :http/request :params)
        query-id (u/decode-edn (-> params :query))
        query-args (u/decode-edn (-> params :args))
        responder (responder query-id)]
    (u/encode-edn (query-sync responder query-args context))))
