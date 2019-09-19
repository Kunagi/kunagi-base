(ns kunagi-base.modules.events
  (:require
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-extension]]
   [kunagi-base.modules.events.api :as ev]))


(def-module
  {:module/id ::events})


(def-extension
  {:schema {:event-handler/module {:db/type :db.type/ref}
            :event/module {:db/type :db.type/ref}
            :event/ident {:db/unique :db.unique/identity}}})


(defn def-event-handler [event-handler]
  (utils/assert-entity
   event-handler
   {:req {:event-handler/module ::am/entity-ref}}
   (str "Invalid event-handler " (-> event-handler :event-handler/id) "."))

  (am/register-entity
   :event-handler
   event-handler))


(defn def-event [event]
  (utils/assert-entity
   event
   {:req {:event/module ::am/entity-ref}}
   (str "Invalid event " (-> event :event/id) "."))

  (am/register-entity
   :event
   event))
