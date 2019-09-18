(ns kunagi-base.event-sourcing.fs-projector
  (:require
   [kunagi-base.utils :as utils]))


(defn- handle-error [ag ex]
  (tap> [:err ::projection-agent-failed ex]))


(def base-path "app-data/event-sourcing/projections")


(defn- filename [o]
  (cond
    (string? o) o
    (simple-keyword? o) (name o)
    (qualified-keyword? o) (str (namespace o) "/" (name o))
    :else (str o)))


(defn projection-path [aggregate-ident projection-ident aggregate-id]
  (str base-path
       "/" (filename aggregate-ident)
       "/" (filename projection-ident)
       "/" (filename aggregate-id)))


(defn- pointer-file-path [aggregate-ident projection-ident aggregate-id]
  (str (projection-path aggregate-ident projection-ident aggregate-id)
       "/latest-pointer.edn"))


(defn read-projection [pointer-file]
  (when (.exists pointer-file)
    (let [tx-id (read-string (slurp pointer-file))
          projection-file-path (str (-> pointer-file .getParent)
                                    "/" tx-id ".edn")]
      (tap> [:dbg ::loading projection-file-path])
      (read-string (slurp projection-file-path)))))


(defn- init-projection-agent [[aggregate-ident projection-ident aggregate-id]]
  (let [path (projection-path aggregate-ident projection-ident aggregate-id)
        pointer-file-path (pointer-file-path aggregate-ident projection-ident aggregate-id)
        pointer-file (-> pointer-file-path java.io.File.)]
    {:aggregate-ident aggregate-ident
     :projection-ident projection-ident
     :aggregate-id aggregate-id
     :path path
     :projection (read-projection pointer-file)}))


(defonce workers-pool (utils/new-worker-agents-pool handle-error init-projection-agent))


(def projection-agent (-> workers-pool :get-f))


(defn write-projection [projection aggregate projection-ident]
  ;; (tap> [:!!! ::write-projection {:aggregate aggregate
  ;;                                 :projection projection}])
  (let [tx-id (-> projection :tx-id)
        [aggregate-ident aggregate-id] aggregate
        path (projection-path aggregate-ident projection-ident aggregate-id)
        projection-file-path (str path "/" tx-id ".edn")
        pointer-file-path (pointer-file-path aggregate-ident projection-ident aggregate-id)]
     (-> path java.io.File. .mkdirs)
     (spit projection-file-path (pr-str projection))
     (spit pointer-file-path (pr-str tx-id))))



(defn- -update-projection [aggregate projection-ident update-f ag]
  (let [projection (-> ag :projection)
        projection (update-f projection)]
    (write-projection projection aggregate projection-ident)
    (assoc ag :projection projection)))


(defn update-projection [aggregate projection-ident update-f]
  (let [[aggregate-ident aggregate-id] aggregate
        !ag (projection-agent [aggregate-ident projection-ident aggregate-id])]
    (send-off !ag (partial -update-projection aggregate projection-ident update-f))))


(defn projection [[aggregate-ident aggregate-id :as aggregate] projection-ident]
  (let [!ag (projection-agent [aggregate-ident projection-ident aggregate-id])
        !p (promise)]
    (send-off !ag (fn [ag]
                    (deliver !p ag)
                    ag))
    @!p))
