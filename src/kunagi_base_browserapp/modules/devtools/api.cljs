(ns kunagi-base-browserapp.modules.devtools.api
  (:require
   [mui-commons.graphed.api :as graphed]
   [kunagi-base-browserapp.modules.devtools.graphed.appmodel-view :as appmodel-view]))


(defn init-graphed [db]
  (tap> [:!!! ::init-graphed :!!!!!!!!!!!!!!!!!!!!!])
  (-> db
      (assoc-in [:graphed :buffers :appmodel]
                (graphed/new-buffer (appmodel-view/->AppmodelView)))))
