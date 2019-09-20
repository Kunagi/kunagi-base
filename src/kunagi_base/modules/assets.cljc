(ns kunagi-base.modules.assets
  (:require
   [clojure.spec.alpha :as s]
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model def-extension]]
   [kunagi-base.modules.events :refer [def-event def-event-handler]]
   [kunagi-base.assets :as assets]))


(def-module
  {:module/id ::assets})


(def-entity-model
  :assets ::asset-pool
  {:asset-pool/ident {:req? true
                      :unique-identity? true
                      :spec keyword?}
   :asset-pool/req-perms {:spec (s/coll-of qualified-keyword?)}
   :asset-pool/load-f {:req? true
                       :spec fn?}
   :asset-pool/load-on-startup {:spec (s/coll-of string?)}
   :asset-pool/dir-path {:spec string?}
   :asset-pool/git-repo? {:spec boolean?}})


(defn def-asset-pool [asset-pool]
  (am/register-entity :asset-pool asset-pool))


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
