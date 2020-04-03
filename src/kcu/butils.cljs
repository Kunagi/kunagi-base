(ns kcu.butils
  "Browser Utilities"
  (:require
   [kcu.utils :as u]))


(defn as-local-storage-key [k]
  (let [k (if (string? k)
            k
            (u/encode-edn k))
        k (u/encode-edn k)]
    k))


(defn get-from-local-storage [k]
  (-> js/window
      .-localStorage
      (.getItem (as-local-storage-key k))
      u/decode-edn))


(defn set-to-local-storage [k v]
  (-> js/window
      .-localStorage
      (.setItem (as-local-storage-key k)
                (u/encode-edn v))))
