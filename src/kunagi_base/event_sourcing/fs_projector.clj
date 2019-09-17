(ns kunagi-base.event-sourcing.fs-projector)

(defonce !agents (atom {}))


(defn- new-agent []
  (agent nil
         :error-handler
         (fn [ag ex] (tap> [:err ::agent-failed ex]))))


(defn- get-agent [path]
  (locking !agents
    (if-let [ag (get-in @!agents path)]
      ag
      (let [ag (new-agent)]
        (swap! !agents assoc-in path ag)
        ag))))


(defn send-off-to [path update-f]
  (let [!ag (get-agent path)]
    (send-off !ag update-f)))


(def base-path "app-data/event-sourcing/projections")


(defn- filename [o]
  (cond
    (string? o) o
    (simple-keyword? o) (name o)
    (qualified-keyword? o) (str (namespace o) "/" (name o))
    :else (str o)))


(defn projection-path [[aggregate-ident aggregate-id] projection-ident]
  (str base-path
       "/" (filename aggregate-ident)
       "/" (filename projection-ident)
       "/" (filename aggregate-id)))


(defn- pointer-file-path [aggregate projection-ident]
  (str (projection-path aggregate projection-ident)
       "/latest-pointer.edn"))


(defn read-projection [pointer-file]
  (let [tx-id (read-string (slurp pointer-file))
        projection-file-path (str (-> pointer-file .getParent)
                                  "/" tx-id ".edn")]
    (tap> [:dbg ::loading projection-file-path])
    (read-string (slurp projection-file-path))))


(defn write-projection [projection aggregate projection-ident]
   (let [tx-id (-> projection :tx-id)
         path (projection-path aggregate projection-ident)
         projection-file-path (str path "/" tx-id ".edn")
         pointer-file-path (pointer-file-path aggregate projection-ident)]
     (-> path java.io.File. .mkdirs)
     (spit projection-file-path (pr-str projection))
     (spit pointer-file-path (pr-str tx-id))))



(defn- new-agent-value [aggregate projection-ident]
  (let [path (projection-path aggregate projection-ident)
        pointer-file-path (pointer-file-path aggregate projection-ident)
        pointer-file (-> pointer-file-path java.io.File.)]
    {:aggregate aggregate
     :projection-ident projection-ident
     :path path
     :projection (when (.exists pointer-file) (read-projection pointer-file))}))


(defn- -update-projection [aggregate projection-ident update-f ag]
  (let [ag (or ag (new-agent-value aggregate projection-ident))
        projection (-> ag :projection)
        projection (update-f projection)]
    (write-projection projection aggregate projection-ident)
    (assoc ag :projection projection)))


(defn update-projection [aggregate projection-ident update-f]
  (let [[aggregate-ident aggregate-id] aggregate]
    (send-off-to [aggregate-ident projection-ident aggregate-id]
                 (partial -update-projection aggregate projection-ident update-f))))
