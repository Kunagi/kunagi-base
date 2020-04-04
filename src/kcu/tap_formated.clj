(ns kcu.tap-formated
  (:require
   [clojure.term.colors :as c]
   [puget.printer :as puget]
   [io.aviso.exception :as aviso]

   [kcu.tap :as kcu-tap]))


(defonce lock :lock)


(defn- print-payload [payload]
  (if (instance? Throwable payload)
    (aviso/write-exception payload)
    (puget/cprint payload {:option :here})))


(defn log-record
  [{:as record :keys [source-ns source-name level payload]}]
  (locking lock
    (let [level-bg (case level
                     :!!! c/on-red
                     :err c/on-red
                     :wrn c/on-red
                     :inf c/on-blue
                     c/on-grey)]
      (println
       (c/on-grey (c/white (str " " (name level) " ")))
       (level-bg (c/white (c/bold (str " " source-name " "))))
       (c/white source-ns))
      (if payload
        (print-payload payload)
        (println))
      (println))))


(reset! kcu-tap/!printer log-record)
