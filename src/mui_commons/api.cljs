(ns mui-commons.api
  (:require
   [re-frame.core :as rf]))

(defn <subscribe [subscription]
  ;; TODO assert subscription
  (if-let [subscription (rf/subscribe subscription)]
    @subscription
    (do
      (tap> [:err ::subscription-missing (first subscription)])
      nil)))


(defn dispatch! [event]
  ;; TODO assert event
  (rf/dispatch event))
