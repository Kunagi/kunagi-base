(ns kunagi-base-browserapp.modules.assets.localstorage
  (:require
   [cljs.reader :refer [read-string]]))


(defn- android? []
  (exists? js/AndroidStorage))


(defn- get-item
  "Returns value of `key' from browser's localStorage."
  [key]
  (if (android?)
    (-> js/AndroidStorage (.readFile key))
    (.getItem (.-localStorage js/window) key)))

(defn- set-item!
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (if (android?)
    (-> js/AndroidStorage (.writeFile key val))
    (.setItem (.-localStorage js/window) key val)))

(defn- remove-item!
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (if (android?)
    (-> js/AndroidStorage (.removeItem key))
    (.removeItem (.-localStorage js/window) key)))


(defn- storage-key [asset-pool asset-path]
  (let [asset-pool-ident (-> asset-pool :asset-pool/ident)]
    (str "assets/"
         (namespace asset-pool-ident)
         "/"
         (name asset-pool-ident)
         "/"
         (-> asset-path))))


(defn load-asset [asset-pool asset-path]
  (when (-> asset-pool :asset-pool/localstorage?)
    (when-let [s (get-item (storage-key asset-pool asset-path))]
      (tap> [:dbg ::load-asset s])
      (read-string s))))


(defn store-asset [asset-pool asset-path value]
  (when (-> asset-pool :asset-pool/localstorage?)
    (tap> [:dbg ::store-asset asset-pool asset-path])
    (set-item! (storage-key asset-pool asset-path) (pr-str value))))
