(ns kcu.mui.snackbars
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]))


(defonce CURRENT (r/atom nil))
(defonce QUEUE (r/atom []))


(declare close-current)


(defn dispose-current []
  (let [snackbar-queue @QUEUE]
    (if (empty? snackbar-queue)
      (reset! CURRENT nil)
      (let [next-snackbar (first snackbar-queue)
            snackbar-queue (into [] (rest snackbar-queue))]
        (js/setTimeout #(close-current)
                       3000)
        (reset! CURRENT next-snackbar)
        (reset! QUEUE snackbar-queue)))))


(defn close-current []
  (js/setTimeout #(dispose-current)
                 300)
  (swap! CURRENT assoc :open false))


(defn show [snackbar]
  (let [snackbar (assoc snackbar :open? true)]
    (if @CURRENT
      (swap! QUEUE conj snackbar)
      (do
        (js/setTimeout #(close-current)
                       3000)
        (reset! CURRENT snackbar)))))


(defn Snackbars []
  [:div.Snackbar
   (when-let [snackbar @CURRENT]
     [:> mui/Snackbar
      {:open (-> snackbar :open?)
       :message (-> snackbar :message)}])])
