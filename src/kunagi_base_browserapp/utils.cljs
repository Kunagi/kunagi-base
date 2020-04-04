(ns kunagi-base-browserapp.utils
  (:require
   [clojure.string :refer [split includes?]]))


(defn scroll-to-top! []
  (js/window.scrollTo 0 0))


(defn parse-location-params
  "Parse URL parameters into a hashmap"
  []
  (let [url (-> (.-location js/window) str)
        param-strs (when (includes? url "?")
                     (-> url (split #"\?") last (split #"\&")))]
    (into {} (for [[k v] (map #(split % #"=") param-strs)]
               [(keyword (js/decodeURIComponent k)) (js/decodeURIComponent v)]))))
