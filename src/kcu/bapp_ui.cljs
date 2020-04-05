(ns kcu.bapp-ui
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.components :as muic]
   [kcu.bapp :as bapp]))


;;; auth ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn PermissionBoundary [req-perm protected-component alternative-component]
  [:div.PermsBoundary
   (if (bapp/user-has-permission? req-perm)
     protected-component
     alternative-component)])


;;; sente ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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


;;; debug helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn DevModeBoundary [& dev-only-components]
  (into
   [:div.DevModeBoundary]
   (when (bapp/dev-mode?) dev-only-components)))


(defn DebugUser []
  [muic/Data :bapp/user (bapp/user)])


(defn DebugCurrent []
  [muic/Stack-1
   [muic/Card
    [DebugUser]]])
