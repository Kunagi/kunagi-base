(ns kcu.butils
  "Browser Utilities"
  (:require
   [reagent.core :as r]
   [kcu.utils :as u]))


(defn as-local-storage-key [k]
  (if (string? k) k (u/encode-edn k)))


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


(defn durable-ratom [k default-value]
  (let [value (get-from-local-storage k)
        ratom (r/atom (or value default-value))]
    (add-watch ratom ::durable-ratom
               (fn [_ _ old-val new-val]
                 (when (not= old-val new-val)
                   (set-to-local-storage k new-val))))
    ratom))
