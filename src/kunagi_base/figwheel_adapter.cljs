(ns ^:figwheel-hooks kunagi-base.figwheel-adapter
  (:require
   [kunagi-base.logging.tap]))


(defn ^:after-load on-figwheel-after-load []
  (tap> [::debug "!!! on-figwheel-after-load"]))

