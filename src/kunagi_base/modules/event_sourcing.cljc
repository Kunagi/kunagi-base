(ns kunagi-base.modules.event-sourcing
  (:require
   [kunagi-base.appmodel :refer [def-module def-extension]]
   [kunagi-base.modules.events :refer [def-event-handler]]
   [kunagi-base.event-sourcing.api :as impl]))


(def-module
  {:module/id ::event-sourcing})


(def-extension
  {:schema {:aggregator/module {:db/type :db.type/ref}
            :aggregator/id {:db/unique :db.unique/identity}
            :projector/module {:db/type :db.type/ref}
            :projector/id {:db/unique :db.unique/identity}
            :projector/aggregator {:db/type :db.type/ref}
            :command/module {:db/type :db.type/ref}
            :command/id {:db/unique :db.unique/identity}
            :command/ident {:db/unique :db.unique/identity}
            :command/aggregator {:db/type :db.type/ref}}})


;; TODO deprecated (use trigger-command! directly)
(def-event-handler
  {:event-handler/id ::command
   :event-handler/module [:module/ident :event-sourcing]
   :event-handler/event-ident :kunagi-base/command-triggered
   :event-handler/f impl/on-command-triggered})
