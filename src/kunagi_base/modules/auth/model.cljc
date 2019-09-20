(ns kunagi-base.modules.auth.model
  (:require
   [kunagi-base.modules.event-sourcing.model]

   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base.modules.event-sourcing.api :as es :refer [def-aggregator def-command def-projector]]

   [kunagi-base.modules.auth.aggregators.oauth-users.projections.oauth-users :as p-oauth-users]
   [kunagi-base.modules.auth.aggregators.oauth-users :as c-oauth-users]

   [kunagi-base.modules.auth.aggregators.oauth-userinfos.projections.oauth-userinfos :as p-oauth-userinfos]
   [kunagi-base.modules.auth.aggregators.oauth-userinfos :as c-oauth-userinfos]))

   ;; [kunagi-base.auth.p-users-perms :as p-users-perms]
   ;; [kunagi-base.auth.c-users-perms :as c-users-perms]))


(def-module
  {:module/id ::auth})


;;; oauth users for identification

(def-aggregator
  {:aggregator/id ::oauth-users
   :aggregator/ident :oauth-users
   :aggregator/module [:module/ident :auth]})

(def-command
  {:command/id ::sign-in-with-oauth
   :command/ident :auth/sign-in-with-oauth
   :command/module [:module/ident :auth]
   :command/aggregator [:aggregator/id ::oauth-users]
   :command/f c-oauth-users/sign-in})

(def-projector
  {:projector/id ::oauth-users
   :projector/ident :oauth-users
   :projector/module [:module/ident :auth]
   :projector/aggregator [:aggregator/id ::oauth-users]
   :projector/apply-event-f p-oauth-users/apply-event})


;;; oauth userinfos

(def-aggregator
  {:aggregator/id ::oauth-userinfos
   :aggregator/ident :oauth-userinfos
   :aggregator/module [:module/ident :auth]})

(def-command
  {:command/id ::process-userinfo
   :command/ident :auth/process-userinfo
   :command/module [:module/ident :auth]
   :command/aggregator [:aggregator/id ::oauth-userinfos]
   :command/f c-oauth-userinfos/process-userinfo})

(def-projector
  {:projector/id ::oauth-userinfos
   :projector/ident :oauth-userinfos
   :projector/module [:module/ident :auth]
   :projector/aggregator [:aggregator/id ::oauth-userinfos]
   :projector/apply-event-f p-oauth-userinfos/apply-event})


;;; permissions

(def-aggregator
  {:aggregator/id ::users-perms
   :aggregator/ident :users-perms
   :aggregator/module [:module/ident :auth]})


;; (def-command
;;   {:command/id ::users-perms
;;    :command/ident :auth/users-perms
;;    :command/aggregator [:aggregator/id ::users-perms]
;;    :command/f c-users-perms/update-users-permissions})

;; (def-projector
;;   {:projector/id ::users-perms
;;    :projector/ident :users-perms
;;    :projector/aggregator [:aggregator/id ::users-perms]
;;    :projector/apply-event-f p-users-perms/apply-event})
