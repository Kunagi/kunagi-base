(ns kcu.query
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]))



(s/def ::responder map?)
(s/def ::responder-id qualified-keyword?)
(s/def ::responder-or-id (s/or :responder ::responder
                               :id ::responder-id))

(defonce !responders (atom {}))


(defn responder
  [id]
  (get @!responders id))


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
  [[responder-or-id query-args] context]
  (u/assert-spec ::responder-or-id responder-or-id "query-sync")
  (let [responder (if (keyword? responder-or-id)
                    (or (responder responder-or-id)
                        (throw (ex-info (str "No responder for query `" responder-or-id "`.")
                                        {:responder responder-or-id
                                         :query-args query-args})))
                    responder)
        f (u/getm responder :f)
        _ (tap> [:dbg ::query [(-> responder :id) query-args]])
        response (f context query-args)]
    response))
