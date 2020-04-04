(ns kunagi-base-browserapp.modules.tracking.model
  (:require
   [clojure.spec.alpha :as s]

   [kunagi-base-browserapp.subs]
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]
   [kunagi-base.modules.startup.model :refer [def-init-function]]

   [kunagi-base-browserapp.modules.tracking.rf-events]
   [kunagi-base-browserapp.modules.tracking.api :as tracking]))


(def-module
 {:module/id ::tracking})

(def-init-function
  {:init-function/id ::install-error-handler
   :init-function/module [:module/ident :tracking]
   :init-function/f (fn [db]
                      (tracking/install-error-handler!)
                      db)})
