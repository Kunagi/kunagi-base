(ns kcu.enable-asserts
  (:require
   [clojure.spec.alpha :as s]))


(s/check-asserts true)
(tap> [:dbg ::spec-asserts-enabled])
