(ns kcu.tap)


#?(:cljs (enable-console-print!))


(defn log-record
  [{:as record :keys [source-ns source-name level payload]}]
  (#?(:clj println
      :cljs js/console.log)
   (str " " (name level) " ")
   source-ns
   source-name
   (if payload payload "")))


(defonce !printer (atom log-record))


(def levels #{:!!! :dbg :inf :wrn :err})


(defn vector-with-level-and-source->record
  [level source v]
  (let [[source-ns source-name] (if (qualified-keyword? source)
                                  [(namespace source) (name source)]
                                  ["unknown" (name source)])]
    {:source-ns source-ns
     :source-name source-name
     :level level
     :payload (case (count v)
                0 nil
                1 (first v)
                (into [] v))}))


(defn vector-with-level->record
  [level [a1 & tail :as v]]
  (cond
    (keyword? a1) (vector-with-level-and-source->record level a1 tail)
    :else {:source-ns "unknown"
           :source-name "anonymous"
           :level level
           :payload (case (count tail)
                      0 nil
                      1 (first tail)
                      (into [] tail))}))


(defn vector->record
  [[a1 & tail :as v]]
  (if (levels a1)
    (vector-with-level->record a1 tail)
    (vector-with-level->record :dbg v)))


(defn o->record [o]
  (cond
    (qualified-keyword? o) {:source-ns (namespace o)
                            :source-name (name o)
                            :level :dbg}
    (simple-keyword? o) {:source-ns "unknown"
                         :source-name (name o)
                         :level :dbg}
    (vector? o) (vector->record o)
    :else {:source-ns "unknown"
           :source-name "anonymous"
           :level :dbg
           :payload o}))


(defn log-object [o]
  (@!printer (o->record o)))


;; (tap> ::just-qualified-keyword)
;; (tap> :just-simple-keyword)
;; (tap> [:wrn ::perfect {:log "warning here"}])
;; (tap> [::perfect {:log "debug here"}])
;; (tap> [::here :we :go :again :and])

(defn register-tap
  []
  (add-tap log-object))


(defonce registered?
  (do
    (register-tap)
    (println "tap> logging activated")
    true))
