(ns kunagi-base-browserapp.modules.sed-ui.editor
  (:require
   [re-frame.core :as rf]

   [mui-commons.api :refer [<subscribe dispatch!]]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kunagi-base.sed.editor :as editor]))


(defn Node [node]
  (let [text (-> node :text)
        childs (-> node :childs)
        payload (-> node :payload)]
    [muic/Card
     {:on-click (when payload
                  #(do
                    (dispatch! [:sed/node-action payload])
                    (-> % .stopPropagation)))
      :style {:cursor (when payload :pointer)}}
     (if (empty? childs)
       [muic/Text text]
       [muic/Stack
        {:spacing (theme/spacing 1)}
        [muic/Text text]
        [muic/Stack
         {:items childs
          :template [Node]}]])]))




(defn Editor []
  (let [editor (<subscribe [:sed/editor])
        node (-> editor :root-node)]
    [:div
     [Node node]
     [muic/Data editor]]))



;; subscriptions

(rf/reg-sub
 :sed/editor
 (fn [db _]
   (get db :sed/editor)))


(rf/reg-event-db
 :sed/node-action
 (fn [db [_ node-payload]]
   (let [editor (get db :sed/editor)
         node-action-handler (editor/node-action-handler editor)]
     (if node-action-handler
       (node-action-handler db node-payload)
       db))))
