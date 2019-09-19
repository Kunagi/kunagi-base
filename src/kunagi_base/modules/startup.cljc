(ns kunagi-base.modules.startup
  (:require
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-extension]]))


(def-module
  {:module/id ::startup})


(def-extension
  {:schema {:init-function/module {:db/type :db.type/ref}}})


(defn def-init-function [init-function]
  (utils/assert-entity
   init-function
   {:req {:init-function/module ::am/entity-ref}}
   (str "Invalid init-function " (-> init-function :init-function/id) "."))

  (am/register-entity
   :init-function
   init-function))
