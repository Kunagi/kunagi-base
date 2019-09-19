(ns kunagi-base.appconfig.file-loader
  (:require
   [clojure.java.io :as io]))


(defn load-edn-file [file]
  (let [file (io/as-file file)]
    (if-not (.exists file)
      nil
      (do
        (tap> [:inf ::loading-config-file (.getAbsolutePath file)])
        (-> file slurp read-string)))))



(defn load-first-existing-file [& files]
  (reduce
   (fn [prev-result file]
     (if prev-result
       prev-result
       (load-edn-file file)))
   nil
   files))
