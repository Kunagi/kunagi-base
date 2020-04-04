(ns kunagi-base-browserapp.modules.desktop.model
  (:require
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]
   [kunagi-base-browserapp.modules.tracking.model]
   [kunagi-base-browserapp.modules.desktop.components]
   [kunagi-base.modules.startup.model :refer [def-init-function]]

   [kunagi-base-browserapp.modules.desktop.api :as impl]))


(def-module
  {:module/id ::desktop})


(def-init-function
  {:init-function/id ::install-accountant
   :init-function/module [:module/ident :desktop]
   :init-function/f (fn [db]
                      (impl/install-accountant!)
                      db)})


;; (def-init-function
;;   {:init-function/id ::install-error-handler
;;    :init-function/module [:module/ident :desktop]
;;    :init-function/f (fn [db]
;;                       (impl/install-error-handler)
;;                       db)})


(def-entity-model
  :desktop ::page
  {:page/ident {:uid? true :spec simple-keyword?}
   :page/workarea {:req true}
   :page/toolbar {}
   :page/title-text {}
   :page/back-button {}
   :page/on-activate-f {:spec fn?}})


(defn def-page [page]
  (am/register-entity :page page))
