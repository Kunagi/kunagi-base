(ns kunagi-base.modules.events
  (:require
   [clojure.spec.alpha :as s]
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]
   [kunagi-base.modules.events.api :as ev]))


(def-module
  {:module/id ::events})


;;; event


(def-entity-model
  :events ::event
  {:event/ident {:uid? true :spec qualified-keyword?}
   :event/req-perms {:spec (s/coll-of qualified-keyword?)}})


(defn def-event [event]
  (am/register-entity :event event))


;;; event-handler


(def-entity-model
  :events ::event-handler
  {:event-handler/ident {:uid? true :spec simple-keyword?}
   :event-handler/event-ident {:req? true :spec qualified-keyword?}
   :event-handler/f {:req? true :spec fn?}})


(defn def-event-handler [event-handler]
  (am/register-entity :event-handler event-handler))
