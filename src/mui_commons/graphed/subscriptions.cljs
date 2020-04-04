(ns mui-commons.graphed.subscriptions
  (:require
   [re-frame.core :as rf]

   [mui-commons.graphed.api :as graphed]
   [mui-commons.graphed.data-view :as data-view]))


(rf/reg-sub
 :graphed/buffers-root-node-id
 (fn [db [_ buffer-id]]
   (when-let [buffer (-> db :graphed :buffers (get buffer-id))]
      (graphed/buffers-root-node-id buffer))))


(rf/reg-sub
 :graphed/buffers-node
 (fn [db [_ buffer-id node-id]]
   (tap> [:!!! ::buffers-node {:buffer-id buffer-id
                               :node-id node-id
                               :db (-> db :graphed)}])
   (when-let [buffer (-> db :graphed :buffers (get buffer-id))]
     (graphed/buffers-node buffer node-id))))
