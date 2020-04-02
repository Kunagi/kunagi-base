(ns kcu.files
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]

   [kcu.utils :as u]))


(defn write-edn
  ([file data]
   (write-edn file data true))
  ([file data pretty?]
   (let [file (io/as-file file)
         dir (-> file .getParentFile)]
     (when-not (-> dir .exists) (-> dir .mkdirs))
     (spit file (u/encode-edn data pretty?)))))


(defn append-edn
  ([file data]
   (append-edn file data true))
  ([file data pretty?]
   (let [file (io/as-file file)
         dir (-> file .getParentFile)]
     (when-not (-> dir .exists) (-> dir .mkdirs))
     (spit file
           (if pretty?
             (str "\n" (u/encode-edn data pretty?))
             (u/encode-edn data pretty?))
           :append true))))


(defn write-entity
  "Writes `entity` as EDN to a file in `dir`.
  The filename is created from entities `:id` or `:db/id`."
  ([dir entity]
   (write-entity dir entity true))
  ([dir entity pretty-print?]
   (if-let [id (or (get entity :id)
                   (get entity :db/id))]
     (write-edn
      (str (-> dir io/as-file .getPath (str "/" (str id) ".edn")))
      entity
      pretty-print?)
     (throw (ex-info "write-entity failed. Missing `:id` or `:db/id` in entity."
                     {:entity entity
                      :dir dir})))))


(defn write-entities
  ([dir entities]
   (write-entities dir entities true))
  ([dir entities pretty-print?]
   (doseq [entity entities]
     (write-entity dir entity pretty-print?))))


(defn read-edn
  [file]
  (let [file (io/as-file file)]
    (when (-> file .exists)
      (-> file slurp edn/read-string))))


(defn read-entities
  "Reads .edn files from `dir`."
  [dir]
  (let [dir (io/as-file dir)]
    (when (-> dir .exists)
      (map (fn [file]
             (-> file slurp edn/read-string))
           (->> dir
                .listFiles
                (filter #(.endsWith (-> % .getName) ".edn")))))))
