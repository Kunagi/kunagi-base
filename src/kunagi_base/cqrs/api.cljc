(ns kunagi-base.cqrs.api
  (:require
   [clojure.spec.alpha :as s]

   [kunagi-base.appconfig.api :as appconfig]))


(s/def ::query-key qualified-keyword?)
(s/def ::query-responder-ident qualified-keyword?)
(s/def ::query-responder-f fn?)
(s/def ::query-context map?)
(s/def ::query-arg any?)
(s/def ::query-request (s/and vector?
                              (s/cat :query-key ::query-key
                                     :args (s/* ::query-arg))))
(s/def ::query-response (s/or :hit sequential?
                              :empty (s/or :nil nil? :empty empty?)))



(defonce !registry (atom {:query-responders {}}))


(defn def-query-responder
  ([query-key ident respond-f]
   (def-query-responder query-key ident respond-f {}))
  ([query-key ident respond-f options]
   (tap> [:dbg ::def-query-responder ident query-key])
   (s/assert ::query-responder-ident ident)
   (s/assert ::query-key query-key)
   (s/assert ::query-responder-f respond-f)
   (swap! !registry
          assoc-in
          [:query-responders query-key ident]
          (assoc options :respond-f respond-f))))


(defn- results-by-responder
  [context query-request])


(defn- query-result>merge-responses
  [{:as result
    :keys [results-by-responder]}]
  (assoc result
         :results
         (reduce into [] (vals results-by-responder))))


(defn- results-from-responders
  [context query-request]
  (let [query-key (first query-request)
        responders (-> @!registry :query-responders (get query-key))]
    (when (empty? responders)
      (tap> [:wrn ::no-responders-defined-for-query {:query query-request
                                                     :available (-> @!registry :query-responders :keys)}]))
    (reduce
     (fn [result [responder-identifier {:as responder :keys [respond-f]}]]
       (let [response (respond-f context query-request)]
         (s/assert ::query-response response)
         (into result response)))
     []
     responders)))


(defn query-sync [context query-request]
  (s/assert ::query-context context)
  (s/assert ::query-request query-request)
  (tap> [:dbg ::query-sync query-request])
  (let [results (results-from-responders context query-request)]
    {:context context
     :results results
     :runtime 0}))


(defn query-sync-r [context query-request]
  (-> (query-sync context query-request)
      :results))

(defn query-sync-r1 [context query-request]
  (-> (query-sync context query-request)
      :results
      first))
