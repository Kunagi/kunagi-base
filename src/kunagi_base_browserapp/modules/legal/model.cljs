(ns kunagi-base-browserapp.modules.legal.model
  (:require
   [clojure.spec.alpha :as s]

   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]
   [kunagi-base-browserapp.modules.desktop.model :refer [def-page]]
   [kunagi-base.modules.startup.model :refer [def-init-function]]

   [kunagi-base-browserapp.modules.legal.components.consents :as consents]))



(def-module
  {:module/id ::legal})


(def-page
  {:page/id ::legal-consents
   :page/ident :legal-consents
   :page/module [:module/ident :legal]
   :page/workarea [consents/Workarea]
   :page/title-text "Einstellungen zu Cookies und Datenverarbeitung"}) ;; TODO i18n


(def-entity-model
  :legal ::consent
  {:consent/ident {:uid? true :spec qualified-keyword?}
   :consent/title {:spec string?}
   :consent/text {:spec string?}})


(defn def-consent [consent]
  (am/register-entity :consent consent))
