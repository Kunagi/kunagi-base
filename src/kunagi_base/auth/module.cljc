(ns kunagi-base.auth.module
  (:require
   [kunagi-base.startup :refer [def-init-function]]
   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base.event-sourcing.api :as es]

   [kunagi-base.auth.p-oauth-users :as p-oauth-users]
   [kunagi-base.auth.c-oauth-users :as c-oauth-users]
   [kunagi-base.auth.p-oauth-userinfos :as p-oauth-userinfos]
   [kunagi-base.auth.c-oauth-userinfos :as c-oauth-userinfos]))


(def-module
  {:module/id ::auth
   :module/ident :auth})


;;; oauth users for identification


(es/def-aggregator
  {:aggregator/id ::oauth-users
   :aggregator/ident :oauth-users})


(es/def-projector
  {:projector/id ::oauth-users
   :projector/ident :oauth-users
   :projector/aggregator [:aggregator/id ::oauth-users]
   :projector/apply-event-f p-oauth-users/apply-event})


(es/def-command
  {:command/id ::sign-in-with-oauth
   :command/ident :auth/sign-in-with-oauth
   :command/aggregator [:aggregator/id ::oauth-users]
   :command/f c-oauth-users/sign-in})


;;; oauth userinfos


(es/def-aggregator
  {:aggregator/id ::oauth-userinfos
   :aggregator/ident :oauth-userinfos})


(es/def-projector
  {:projector/id ::oauth-userinfos
   :projector/ident :oauth-userinfos
   :projector/aggregator [:aggregator/id ::oauth-userinfos]
   :projector/apply-event-f p-oauth-userinfos/apply-event})


(es/def-command
  {:command/id ::process-userinfo
   :command/ident :auth/process-userinfo
   :command/aggregator [:aggregator/id ::oauth-userinfos]
   :command/f c-oauth-userinfos/process-userinfo})


;; (def-init-function
;;   {:init-function/id ::debug
;;    :init-function/f
;;    (fn [db]
;;      (es/aggregate-events
;;       [:auth/oauth-users "sigleton"]
;;       [
;;        [:user-signed-up
;;         {:user-id "5c876588-d3b9-4951-8b0b-9fc00a5f3373"
;;          :oauth-id [:google "12345"]}]])

;;      (es/aggregate-events
;;       [:auth/oauth-userinfos "sigleton"]
;;       [
;;        [:oauth-userinfo-received
;;         {:service :google
;;          :userinfo {:sub "12345"
;;                     :email "witoslaw.koczewski@gmail.com"
;;                     :name "Witoslaw Koczewski"
;;                     :some-other-stuff "1234"}}]])
;;      db)})
