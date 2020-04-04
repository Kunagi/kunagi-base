(ns kcu.sapp
  "Server Application"
  (:require
   [clojure.spec.alpha :as s]
   [ring.util.request :as ring-request]
   [io.aviso.exception :as aviso-exception]

   [kcu.utils :as u]
   [kcu.query :as query]
   [kcu.txa :as txa]
   [kcu.system :as system]
   [kcu.sapp-conversation :as conversation]
   [kcu.files :as files]))


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


(def query-sync query/query-sync)

(defn query-async
  [query context callback]
  (future
    (callback (query/query-sync query context))))

(reset! conversation/!query-f query/query-sync)


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
;;; system


(defn aggregate-dir [aggregator-id aggregate-id]
  (str data-dir
       "/aggregates/"
       (name aggregator-id)
       (when aggregate-id
         (str "/" (cond
                    (simple-keyword? aggregate-id) (name aggregate-id)
                    (qualified-keyword? aggregate-id)
                    (str (namespace aggregate-id) "_" (name aggregate-id))
                    :else aggregate-id)))))


(defn aggregate-value-file [aggregator-id aggregate-id]
  (str (aggregate-dir aggregator-id aggregate-id) "/value.edn"))


(defn aggregate-storage []
  (reify system/AggregateStorage

    (system/store-aggregate-value [_this aggregator-id aggregate-id value]
      (files/write-edn (aggregate-value-file aggregator-id aggregate-id) value))

    (system/store-aggregate-effects [_this aggregator-id aggregate-id effects]
      (let [dir (aggregate-dir aggregator-id aggregate-id)
            groups (group-by (fn [effect]
                               (or (when (-> effect :event/name) :events)
                                   :effects))
                             effects)]
        (doseq [[group-key effects] groups]
          (doseq [effect effects]
            (tap> [:!!! ::store {:effect effect}])
            (files/append-edn (str dir "/" (name group-key) ".edn") effect)))))

    (system/load-aggregate-value [_this aggregator-id aggregate-id]
      (files/read-edn (aggregate-value-file aggregator-id aggregate-id)))))


(defn projection-value-file [projector-id projection-id]
  (str data-dir
       "/projections/"
       (name projector-id)
       (when projection-id
         (str "/" (cond
                    (simple-keyword? projection-id) (name projection-id)
                    (qualified-keyword? projection-id)
                    (str (namespace projection-id) "_" (name projection-id))
                    :else projection-id)))
       ".edn"))


(defn projection-storage []
  (reify system/ProjectionStorage

    (system/store-projection-value [_this projector-id projection-id value]
      (files/write-edn (projection-value-file projector-id projection-id) value))

    (system/load-projection-value [_this projector-id projection-id]
      (files/read-edn (projection-value-file projector-id projection-id)))))


(defonce system (system/new-system
                 :sapp
                 {:aggregate-storage (aggregate-storage)
                  :projection-storage (projection-storage)}))


(defn dispatch
  ([command]
   (dispatch command nil))
  ([command callback]
   (system/dispatch-command system command callback)))


(defn dispatch-from-bapp [command callback context]
  ;; FIXME check permission
  (system/dispatch-command system command callback))


(defn subscribe [query on-change-f context]
  ;; FIXME check permission
  (let [projector-id (-> query :projection/projector)
        projection-id (-> query :projection/id)]
    (system/subscribe-to-projection system
                                    projector-id projection-id
                                    on-change-f)))


(comment
  (do

    (def system (system/new-system :test-sapp {:aggregate-storage (aggregate-storage)}))

    (system/dispatch-command system
                             {:command/name :wartsapp/ziehe-nummer
                              :patient/id "p1"})

    (-> system :transactions deref first :tx-num)))


(defn- log-environment-info []
  (tap> [:inf ::environment-info
         {:working-directory (System/getProperty "user.dir")
          :user-name (System/getProperty "user.name")
          :user-home (System/getProperty "user.home")
          :java-version (System/getProperty "java.version")
          :locale (-> (java.util.Locale/getDefault) .toString)}]))


;; (defn- complete-appinfo [appinfo]
;;   (assoc
;;    appinfo
;;    :app-name (or (-> appinfo :app-name)
;;                  (-> appinfo :project :id)
;;                  "noname")
;;    :app-version (or (-> appinfo :app-version)
;;                     (str (or (-> appinfo :release :major) 0)
;;                          "."
;;                          (or (-> appinfo :release :minor) 0)))
;;    :app-label (or (-> appinfo :app-label)
;;                   (-> appinfo :project :name)
;;                   "Noname App")))


(defn init []
  (log-environment-info)
  (system/dispatch-event system
                         {:event/name :sapp/initialized}))

(defn main [& args]
  (try
    (init)
    (catch Exception ex
      (aviso-exception/write-exception ex)
      (System/exit 1))))
