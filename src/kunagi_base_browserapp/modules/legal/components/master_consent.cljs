(ns kunagi-base-browserapp.modules.legal.components.master-consent
  (:require
   ["@material-ui/core" :as mui]
   [mui-commons.theme :as theme]
   [mui-commons.components :as muic]))




(defn MasterConsentPage []
  [:> mui/Container
   {:max-width :sm}
   [:br]
   [:> mui/Card
    [:> mui/CardHeader
     {:title "Einwilligung zur Datenverarbeitung"}]
    [:> mui/CardContent
     [:div
      "Diese App bzw. Webseite ist ein Angebot von "
      "Frankenburg Softwaretechnik GmbH, Unter der Frankenburg 20, 31737 Rinteln. Geschäftsführer Fabian Hager und Witoslaw Koczewski. Amtsgericht Stadthagen HRB 201547. Umsatzsteuer-ID DE322317556. Telefon +49 5751 9934900. E-Mail mail@fraknenburg.software."]
     [:br]
     [:div
      " Dürfen wir"
      " "

      "zur Verbesserung unseres Angebots"
      " und "
      "für die Schaltung auf Sie zugeschnittener Werbung"

      " "

      "Cookies speichern"
      ", "
      "anonymisierte Daten an Google senden"
      " und "
      "Ihre Nutzung unseres Angebots auswerten"

      "?"]
     [:br]
     [:div
      "Weitere Informationen finden Sie in unserer "
      [muic/ForeignLink
       {:href "http://gesetze.digital/policy.html"}
       "Datenschutzerklärung"]
      "."
      " "
      "Ihre Einwilligung ist freiwillig, sie können diese jederzeit für die Zukunft widerrufen."]]
    [:> mui/CardActions
     {:style {:justify-content :center}}
     ;{:style {:justify-content :space-evenly}}
     [:> mui/Button
      "Detailiert Anpassen"]
     [:div
      {:style {:width (theme/spacing 2)}}]
     [:> mui/Button
      {:variant :contained
       :color :primary}
      "Einverstanden"]]]])


(defn ConsentBoundary [& elements]
  (let [consent-given? true]
    (if consent-given?
      (into [:div] elements)
      [MasterConsentPage])))
