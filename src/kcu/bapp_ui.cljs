(ns kcu.bapp-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [kcu.bapp :as bapp]))


(defn SenteStatusIndicator []
  (let [state @bapp/SENTE_STATE
        open? (-> state :open?)]
    (if open?
      [:> mui/IconButton
       {:disabled true
        :title "Online"
        :size :small
        :style {:color :grey}}
       [:> icons/Link]]
      [:> mui/IconButton
       {:disabled true
        :title "Offline"
        :size :small
        :style {:color :red}}
       [:> icons/LinkOff]])))
