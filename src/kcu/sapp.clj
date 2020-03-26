(ns kcu.sapp
  "Server Application"
  (:require
   [ring.util.request :as ring-request]
   [kcu.utils :as u]
   [kcu.txa :as txa]))


(def data-dir "app-data")

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


(defn serve-conversation
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
