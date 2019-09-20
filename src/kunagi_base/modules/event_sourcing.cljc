(ns kunagi-base.modules.event-sourcing
  (:require
   [kunagi-base.appmodel :refer [def-module def-entity-model]]
   [kunagi-base.modules.events.model :refer [def-event-handler]]
   [kunagi-base.event-sourcing.api :as impl]))


;; TODO def-entity-model
;; TODO move def-methods to here

(def-module
  {:module/id ::event-sourcing})


;;; aggregator


(def-entity-model
  :event-sourcing ::aggregator
  {:aggregator/ident {:uid? true :spec simple-keyword?}
   :aggregator/impl {}})


;;; command


(def-entity-model
  :event-sourcing ::command
  {:command/ident {:uid? true :spec qualified-keyword?} ;; TODO simple-keyword
   :command/aggregator {:ref? true}
   :command/f {:req? true :spec fn?}})


;;; projector


(def-entity-model
  :event-sourcing ::projector
  {:projector/ident {:req? true :spec simple-keyword?}
   :projector/aggregator {:ref? true}
   :projector/apply-event-f {:req? true :spec fn?}})


;;;


;; TODO deprecated (use trigger-command! directly)
(def-event-handler
  {:event-handler/id ::command
   :event-handler/module [:module/ident :event-sourcing]
   :event-handler/event-ident :kunagi-base/command-triggered
   :event-handler/f impl/on-command-triggered})
