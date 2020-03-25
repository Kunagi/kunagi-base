(ns kcu.app-clj
  (:require
   [kcu.init :as init]
   [kcu.init-clj]
   [kcu.events :as events]))


(defn on-db-agent-error [_agent ex]
  (tap> [:err ::on-db-agent-error ex]))


(defonce db (agent {}
                   :error-mode :continue
                   :error-handler on-db-agent-error))


(defn run-effect [effect-id args]
  (tap> [:dbg ::run-effect effect-id args])
  ;; FIXME
  (throw (ex-info (str "run-effect not imlemented")
                  {:effect-id effect-id
                   :args args})))


(defn- handle-event [event context db]
  (let [context (assoc context :db db)
        effects (events/handle-event context event)
        db (get effects :db)
        effects (dissoc effects :db)]
    (doseq [[effect args] effects]
      (run-effect effect args))
    db))


(defn dispatch [event context]
  (send-off db (partial handle-event event context)))


(reset! init/dispatch dispatch)
