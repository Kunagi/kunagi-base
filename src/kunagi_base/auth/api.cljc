(ns kunagi-base.auth.api)


(def default-perms
  #{:auth/authenticated
    :assets/read
    :cqrs/query
    :frankenburg/geschaeftsdaten})


(defn update-context
  [context]
  (if-let [user-id (get context :auth/user-id)]
    (-> context
        (assoc :auth/user-perms default-perms))
    context))


(defn context-authorized?
  [context]
  (-> context :auth/authorized?))


(defn context-has-permission?
  [context req-perm]
  (let [user-id (get context :auth/user-id)]
    (if-not user-id
      false
      (let [user-perms (or (get context :auth/user-perms)
                           #{})]
        ;; (tap> [:err ::permission-check {:user-perms user-perms
        ;;                                 :req-perm req-perm}])
        (user-perms req-perm)))))


(defn context-has-permissions?
  [context req-perms]
  (if (empty? req-perms)
    true
    (let [user-id (get context :auth/user-id)]
      (if-not user-id
        false
        (reduce
         (fn [result req-perm]
           (and result (context-has-permission? context req-perm)))
         true
         req-perms)))))
