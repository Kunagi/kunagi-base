(ns kunagi-base.modules.assets
  (:require
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-extension]]
   [kunagi-base.modules.events :refer [def-event def-event-handler]]
   [kunagi-base.assets :as assets]))


(def-module
  {:module/id ::assets})


(def-extension
  {:schema {:asset-pool/module {:db/type :db.type/ref}}})


(defn def-asset-pool [asset-pool]
  (utils/assert-entity
   asset-pool
   {:req {:asset-pool/module ::am/entity-ref}}
   (str "Invalid asset-pool " (-> asset-pool :asset-pool/id) "."))
  (am/register-entity
   :asset-pool
   asset-pool))


(def-event
  {:event/id ::asset-requested
   :event/ident :assets/asset-requested
   :event/module [:module/ident :assets]
   :event/req-perms [:assets/read]})


(def-event-handler
  {:event-handler/id ::asset-requested
   :event-handler/module [:module/ident :assets]
   :event-handler/event-ident :assets/asset-requested
   :event-handler/f assets/on-asset-requested})
