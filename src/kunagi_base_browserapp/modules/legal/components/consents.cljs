(ns kunagi-base-browserapp.modules.legal.components.consents
  (:require
   ["@material-ui/core" :as mui]
   [mui-commons.theme :as theme]))


(defn Consent [consent]
  (let [checked? (boolean (-> consent :consent))
        property (-> consent :property)]
    [:> mui/Card
     [:> mui/CardContent
      [:div
       {:style {:display :flex
                :justify-content :space-between}}

       [:strong
        (-> property :consent-property/title)]
       [:div
        [:> mui/Switch
         {:checked checked?
          :size :small
          :color :primary}]]]
      [:div
       {:style {:margin-top (theme/spacing 1)}}
       (-> property :consent-property/text)]
      (when-not checked?
        [:div
         {:style {:margin-top (theme/spacing 1)
                  :color (-> (theme/theme) .-palette .-secondary .-main)}}
         (-> property :consent-property/consequence)])]]))


(defn Consents []
  [:div
   {:style {:display :grid
            :grid-gap "1rem"}}
   [Consent
    {:property {:consent-property/title "Personalisierte Werbung"
                :consent-property/text "Provides information about how a web site performs, how each page renders, and whether there are technical issues on the web site to the web site operator. This storage type generally doesn’t collect information that identifies a visitor. "
                :consent-property/consequence "Werbung wird weiterhin angezeigt. Diese ist jedoch nicht auf Sie persönlich zugeschnitten."}
     :consent {:date "2019-11-01"}}]
   [Consent
    {:property {:consent-property/title "Personalisierte Werbung"
                :consent-property/text "Provides information about how a web site performs, how each page renders, and whether there are technical issues on the web site to the web site operator. This storage type generally doesn’t collect information that identifies a visitor. "
                :consent-property/consequence "Werbung wird weiterhin angezeigt. Diese ist jedoch nicht auf Sie persönlich zugeschnitten."}}]])


(defn Workarea []
  [Consents])
