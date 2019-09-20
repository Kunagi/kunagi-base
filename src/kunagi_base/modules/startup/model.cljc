(ns kunagi-base.modules.startup.model
  (:require
   [clojure.spec.alpha :as s]
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am :refer [def-module def-entity-model]]))


(def-module
  {:module/id ::startup})


(def-entity-model
  :startup ::init-function
  {:init-function/f {:req? true :spec fn?}})


(defn def-init-function [init-function]
  (am/register-entity :init-function init-function))
