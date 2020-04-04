(ns kunagi-base-server.modules.assets.model
  (:require
   [clojure.spec.alpha :as s]
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]
   [kunagi-base.modules.events.model :refer [def-event def-event-handler]]
   [kunagi-base.modules.startup.model :refer [def-init-function]]
   [kunagi-base-server.modules.assets.api :as impl]))


;; TODO split into server and browserapp

(def-module
  {:module/id ::assets})


(def-entity-model
  :assets ::asset-pool
  {:asset-pool/ident {:uid? true :spec keyword?}
   :asset-pool/req-perms {:spec (s/coll-of qualified-keyword?)}
   :asset-pool/load-f {:spec fn?}
   :asset-pool/load-on-startup {:spec (s/coll-of string?)}
   :asset-pool/dir-path {:spec string?}
   :asset-pool/git-repo? {:spec boolean?}}) ;; server only


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
   :event-handler/f impl/on-asset-requested})


(def-init-function
  {:init-function/id ::load-startup-assets
   :init-function/module [:module/ident :assets]
   :init-function/f impl/load-startup-assets})
