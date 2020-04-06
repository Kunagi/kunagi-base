(ns kcu.auth.auth
  (:require
   [clojure.spec.alpha :as s]

   [kcu.utils :as u]
   [kcu.aggregator :refer [def-command def-event def-test-flow]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn user-id-by-oauth [this service sub]
  (get-in this [:service->sub->user-id service sub]))


;;; sign up ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def-command :sign-up-with-oauth
  (fn [this args context]
    (let [service (u/getm args :service)
          userinfo (u/getm args :userinfo)
          sub (u/getm userinfo :sub)]
      (if-let [user-id (user-id-by-oauth this service sub)]
        [{:effect/type :result :user/id user-id}] ; user already signed-up
        (let [email (u/getm userinfo :email) ; new user
              user-id (context :random-uuid)]
          [{:effect/type :result :user/id user-id}
           {:event/name :user-signed-up
            :user/id user-id
            :user/email email
            :oauth {:service service
                    :sub sub
                    :userinfo userinfo}}])))))


(defn- assoc-oauth [this event]
  (if-let [oauth (get this :oauth)]
    (let [user-id (u/getm event :user/id)
          service (u/getm oauth :service)
          sub (u/getm oauth :sub)]
      (assoc-in this [:service->sub->user-id service sub] user-id))
    this))


(def-event :user-signed-up
  (fn [this event]
    (let [user-id (u/getm event :user/id)]
      (-> this
          (update :user-ids #(conj (or % #{}) user-id))
          (assoc-oauth event)))))
