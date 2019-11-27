(ns kunagi.datumoj.schema
  (:require
   [clojure.spec.alpha :as s]))


(s/def ::ident simple-keyword?)
