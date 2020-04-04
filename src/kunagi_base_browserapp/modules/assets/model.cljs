(ns kunagi-base-browserapp.modules.assets.model
  (:require
   [clojure.spec.alpha :as s]

   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]
   [kunagi-base.modules.startup.model :refer [def-init-function]]

   [kunagi-base-browserapp.modules.assets.api :as impl]))


(def-module
  {:module/id ::assets})


(def-entity-model
  :assets ::asset-pool
  {:asset-pool/ident {:uid? true :spec qualified-keyword?}
   :asset-pool/req-perms {:spec (s/coll-of qualified-keyword?)}
   :asset-pool/load-on-startup {:spec (s/coll-of string?)}
   :asset-pool/url-path {:spec string?}
   :asset-pool/localstorage {:spec boolean?}
   :asset-pool/request-on-startup {:spec (s/coll-of string?)}
   :asset-pool/asset-received-transformer {:spec fn?}
   :asset-pool/asset-received-handlers {:spec (s/coll-of fn?)}})


(def-init-function
  {:init-function/id ::load-startup-assets
   :init-function/module [:module/ident :assets]
   :init-function/f impl/load-startup-assets})


(def-init-function
  {:init-function/id ::request-startup-assets
   :init-function/module [:module/ident :assets]
   :init-function/f impl/request-startup-assets})


(defn def-asset-pool [asset-pool]
  (am/register-entity :asset-pool asset-pool)
  (impl/reg-sub-for-asset-pool asset-pool))
