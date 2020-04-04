(ns kunagi-base-server.modules.browserapp.model
  (:require
   [kunagi-base.appmodel :refer [def-module]]

   [kunagi-base-server.modules.http-server.model :refer [def-route]]
   [kunagi-base-server.modules.browserapp.api :as impl]))


(def-module
  {:module/id ::browserapp})

(def-route
  {:route/id ::root-redirect
   :route/module [:module/ident :browserapp]
   :route/path "/"
   :route/serve-f impl/serve-redirect-to-app
   :route/req-perms []})

(def-route
  {:route/id ::ui-redirect
   :route/module [:module/ident :browserapp]
   :route/path "/ui"
   :route/serve-f impl/serve-redirect-to-app
   :route/req-perms []})

(def-route
  {:route/id ::app
   :route/module [:module/ident :browserapp]
   :route/path "/ui/**"
   :route/serve-f impl/serve-app
   :route/req-perms []})
