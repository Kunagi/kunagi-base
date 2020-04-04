(ns kunagi-base-browserapp.modules.devtools.model
  (:require
   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base.modules.startup.model :refer [def-init-function]]
   [kunagi-base-browserapp.modules.desktop.model :refer [def-page]]

   [kunagi-base-browserapp.modules.devtools.api :as impl]
   [kunagi-base-browserapp.modules.devtools.tap :as tap]
   [kunagi-base-browserapp.modules.devtools.cards :as cards]))


(def-module
  {:module/id ::devtools})


(def-page
  {:page/id         ::devtools-cards
   :page/ident      :devtools-cards
   :page/module     [:module/ident :devtools]
   :page/title-text "devtools: [Cards]"
   :page/workarea   [cards/Workarea]})


(def-page
  {:page/id ::devtools-tap
   :page/ident :devtools-tap
   :page/module [:module/ident :devtools]
   :page/title-text "devtools: tap>"
   :page/workarea [tap/Workarea]})


;; (def-init-function
;;   {:init-function/id ::graphed
;;    :init-function/module [:module/ident :devtools]
;;    :init-function/f #(impl/init-graphed %)})


(def-init-function
  {:init-function/id ::tap
   :init-function/module [:module/ident :devtools]
   :init-function/f #(tap/init-tap! %)})
