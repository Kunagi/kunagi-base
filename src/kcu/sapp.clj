(ns kcu.sapp
  "Server Application"
  (:require
   [clojure.spec.alpha :as s]
   [ring.util.request :as ring-request]

   [kcu.utils :as u]
   [kcu.txa :as txa]
   [kcu.sapp-conversation :as conversation]
   [kcu.aggregator :as aggregator]))


(def data-dir "app-data")


(defn http-response-missing-param
  [param]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body (str "missing request parameter: "
              (if (simple-keyword? param)
                (name param)
                param))})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; conversations

(s/def ::conversation-id (s/and string?
                                #(> (count %) 8)))

(def conversations-dir (str data-dir "/conversations"))

(defonce !conversations-txas (atom {}))


(defn conversation-txa [conversation-id]
  (u/assert-spec ::conversation-id conversation-id ::conversation-txa)
  (locking !conversations-txas
    (if-let [txa (get @!conversations-txas conversation-id)]
      txa
      (let [txa (txa/new-txa (str "conversation/" conversation-id)
                             {:file (str conversations-dir "/" conversation-id ".edn")
                              :store-exclude-keys [:messages-promise :messages-promise-time]
                              :constructor (fn [_] (conversation/new-conversation conversation-id))})]
        (swap! !conversations-txas assoc conversation-id txa)
        (tap> [:dbg ::new-conversation conversation-id])
        txa))))


(defn http-serve-post-messages
  [context]
  (let [params (-> context :http/request ring-request/body-string u/decode-edn)
        conversation-id (-> params :conversation)
        messages (-> params :messages)
        txa (conversation-txa conversation-id)]
    (txa/transact txa #(conversation/process-client-messages % messages))))


;;; queries

(s/def ::responder map?)
(s/def ::responder-id qualified-keyword?)
(s/def ::responder-or-id (s/or :responder ::responder
                               :id ::responder-id))

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
  [[responder query-args] context]
  (u/assert-spec ::responder-or-id responder "query-sync")
  (let [responder (if (keyword? responder)
                    (u/getm @!responders responder)
                    responder)
        f (u/getm responder :f)
        _ (tap> [:dbg ::query [(-> responder :id) query-args]])
        response (f context query-args)]
      response))

(reset! conversation/!query-f query-sync)

(defn query-async
  [query context callback]
  (future
    (callback (query-sync query context))))


(defn http-serve-query [context]
  ;; TODO respond with error on error response
  (let [params (-> context :http/request :params)
        query (u/decode-edn (-> params :query))]
    (u/encode-edn (query-sync query context))))


(defn flag-subscriptions-in-conversations!
  [responder query-args]
  (let [query [(-> responder :id) query-args]
        txas (vals @!conversations-txas)]
    (doseq [txa txas]
      (txa/transact txa #(conversation/flag-subscription % query)))
    nil))


(defn http-serve-messages [context]
  (let [params (-> context :http/request :params)
        conversation-id (-> params :conversation)
        wait? (and (-> params :wait)
                   (not= "false" (-> params :wait)))]
    (if-not conversation-id
      (http-response-missing-param :conversation)
      (let [txa (conversation-txa conversation-id)
            messages-promise (promise)]
        (txa/transact txa #(conversation/provide-messages-for-client
                            % messages-promise wait?))
        (let [messages (-> messages-promise (deref 5000 []))]
          (u/encode-edn messages))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; aggregator


