(ns kcu.sapp-conversation
  "Server Application Conversation"
  (:require
   [clojure.spec.alpha :as s]
   [clojure.set :as set]
   [kcu.utils :as u]))


(s/def ::query-id qualified-keyword?)
(s/def ::query-args map?)
(s/def ::query (s/cat :id ::query-id :args ::query-args))
(s/def ::queries (s/coll-of ::query))


(defonce !query-f (atom (fn [query context] (throw (ex-info "not implemented" {})))))


(defn- query->response-message [conversation query]
  {:type :query-response
   :query query
   :response (@!query-f query {:conversation conversation})})


(defn- messages-promise [conversation]
  (when-let [messages-promise (-> conversation :messages-promise)]
    (let [promise-time (-> conversation :messages-promise-time)
          age (- (u/current-time-millis) promise-time)]
      (when (< age 4500)
        messages-promise))))


(defn flag-subscription [conversation query]
  (if (contains? (-> conversation :subscriptions) query)
    (if-let [messages-promise (messages-promise conversation)]
      (do
        (deliver messages-promise [(query->response-message conversation query)])
        (-> conversation
            (dissoc :messages-promise)
            (dissoc :messages-promise-time)))
      (update conversation :queries-outbox conj query))
    conversation))


(defn new-conversation [conversation-id]
  {:id conversation-id
   :creation-time (u/current-time-millis)
   :subscriptions #{}
   :queries-outbox '()})


(defn process-client-message--subscriptions
  [conversation subscriptions]
  (u/assert-spec ::queries subscriptions ::process-client-message--subscriptions)
  (let [new-subscriptions (into #{} subscriptions)
        _ (tap> [:!!! ::new-subscriptions new-subscriptions])
        old-subscriptions (-> conversation :subscriptions)
        added-subscriptions (set/difference new-subscriptions old-subscriptions)]
    (reduce (fn [conversation added-subscription]
              _ (tap> [:!!! ::added-subscription added-subscription])
              (flag-subscription conversation added-subscription))
            (assoc conversation :subscriptions new-subscriptions)
            added-subscriptions)))


(defn- process-client-message [conversation message]
  (tap> [:dbg ::process-client-message message])
  (case (-> message :type)
    :subscriptions (process-client-message--subscriptions
                    conversation (-> message :subscriptions))
    ;; TODO query
    (do
      (tap> [:wrn ::unsupported-message message])
      conversation)))


(defn process-client-messages [conversation messages]
  (-> (reduce process-client-message conversation messages)))
      ;; (assoc :last-activity-time (u/current-time-millis))))


(defn provide-messages-for-client [conversation messages-promise wait?]
  (let [queries (-> conversation :queries-outbox)
        messages (-> []
                     (into (map (partial query->response-message
                                         conversation)
                                queries)))]
    (if (and wait? (empty? messages))
      (-> conversation
          (assoc :messages-promise messages-promise)
          (assoc :messages-promise-time (u/current-time-millis)))
      (do
        (deliver messages-promise messages)
        (-> conversation
            (assoc :queries-outbox '()))))))
