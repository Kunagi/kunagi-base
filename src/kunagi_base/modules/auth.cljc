(ns kunagi-base.modules.auth
  (:require
   [kunagi-base.startup :refer [def-init-function]]
   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base.event-sourcing.api :as es]

   [kunagi-base.modules.auth.aggregators.oauth-users.projections.oauth-users :as p-oauth-users]
   [kunagi-base.modules.auth.aggregators.oauth-users :as c-oauth-users]

   [kunagi-base.modules.auth.aggregators.oauth-userinfos.projections.oauth-userinfos :as p-oauth-userinfos]
   [kunagi-base.modules.auth.aggregators.oauth-userinfos :as c-oauth-userinfos]))

   ;; [kunagi-base.auth.p-users-perms :as p-users-perms]
   ;; [kunagi-base.auth.c-users-perms :as c-users-perms]))


(def-module
  {:module/id ::auth
   :module/ident :auth})


;;; oauth users for identification

(es/def-aggregator
  {:aggregator/id ::oauth-users
   :aggregator/ident :oauth-users})

(es/def-command
  {:command/id ::sign-in-with-oauth
   :command/ident :auth/sign-in-with-oauth
   :command/aggregator [:aggregator/id ::oauth-users]
   :command/f c-oauth-users/sign-in})

(es/def-projector
  {:projector/id ::oauth-users
   :projector/ident :oauth-users
   :projector/aggregator [:aggregator/id ::oauth-users]
   :projector/apply-event-f p-oauth-users/apply-event})


;;; oauth userinfos

(es/def-aggregator
  {:aggregator/id ::oauth-userinfos
   :aggregator/ident :oauth-userinfos})

(es/def-command
  {:command/id ::process-userinfo
   :command/ident :auth/process-userinfo
   :command/aggregator [:aggregator/id ::oauth-userinfos]
   :command/f c-oauth-userinfos/process-userinfo})

(es/def-projector
  {:projector/id ::oauth-userinfos
   :projector/ident :oauth-userinfos
   :projector/aggregator [:aggregator/id ::oauth-userinfos]
   :projector/apply-event-f p-oauth-userinfos/apply-event})


;;; permissions

;; (es/def-aggregator
;;   {:aggregator/id ::users-perms
;;    :aggregator/ident :users-perms})

;; (es/def-command
;;   {:command/id ::users-perms
;;    :command/ident :auth/users-perms
;;    :command/aggregator [:aggregator/id ::users-perms]
;;    :command/f c-users-perms/update-users-permissions})

;; (es/def-projector
;;   {:projector/id ::users-perms
;;    :projector/ident :users-perms
;;    :projector/aggregator [:aggregator/id ::users-perms]
;;    :projector/apply-event-f p-users-perms/apply-event})
