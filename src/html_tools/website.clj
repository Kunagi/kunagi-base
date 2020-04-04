(ns html-tools.website
  (:require
   [clojure.java.io :as io]

   [html-tools.server :as server]
   [html-tools.api :as html-tools]))


(defn generate-page-file [target file-name page-config]
  (let [file (str target "/" file-name)]
    (println "    *" file-name)
    (html-tools/write-page! page-config file)))

(defn same-file? [a b]
  (and
   (= (.getName a) (.getName b))
   (= (.length a) (.length b))))

(defn copy-file [target dir file-name]
  (let [from-file (-> (str "resources/" dir file-name) io/as-file)
        to-file (-> (str target "/" dir file-name) io/as-file)]
    ;; (println "    *" (.getPath from-file) "->" (.getPath to-file))
    (if (.isDirectory from-file)
      (do
        (.mkdir to-file)
        (doall
         (for [file (.listFiles from-file)]
           (copy-file target (str dir (.getName from-file) "/") (.getName file)))))
      (when (not (same-file? from-file to-file))
        (do
          (println "    *" (str dir file-name))
          (java.nio.file.Files/copy
           (.toPath from-file)
           (.toPath to-file)
           (into-array java.nio.file.CopyOption
                       [java.nio.file.StandardCopyOption/REPLACE_EXISTING])))))))



(defn generate-files [target website-config]
  (println "generating website")
  (println "  target:" target)

  (println "  generating pages")
  (doall
   (for [[file-name page-config] (get website-config :pages)]
     (generate-page-file target file-name page-config))))


(defn copy-resources [target]
  (println "  copying resources")
  (doall
   (for [file (-> "resources" io/as-file .listFiles)]
     (copy-file target "" (.getName file)))))


(defn generate [target website-config]
  (case target

    "!http"
    (server/run-http-server website-config)

    (do
      (generate-files target website-config)
      (when (:copy-resources? website-config)
        (copy-resources target)))))
