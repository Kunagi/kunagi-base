(ns mui-commons.graphed.ui-components
  (:require
   ["@material-ui/core" :as mui]
   [re-frame.core :as rf]

   [mui-commons.components :as muic]
   [mui-commons.graphed.subscriptions]))


(def spacing "5px")


(declare !Node)

(defn NodeName [text type]
  [:div.NodeName
   [:> mui/Typography
    {:variant :caption
     :style {:overflow :auto
             :color (case type
                      :string "#006600"
                      :keyword "#660000"
                      "#000000")}}
    (str text)]])


(defn NodeSideText [text]
  [:div.NodeSideText
   [:> mui/Typography
    {:variant :caption
     :style {:color "#aaa"
             :margin-right spacing}}
    (str text)]])


(defn NodeChildren [buffer-id depth horizontal? childs]
  (into
   [:div.NodeChildren
    {:style {:display :grid
             :grid-template-columns (if horizontal?
                                      "repeat(2, auto)")
             :align-items :start
             :justify-items :start
             :grid-gap spacing}}]
   ;;:margin-left spacing}}]
   childs))



(defn Node
  [buffer-id node depth]
  [:> mui/Paper
   {:style {:padding spacing
            :background-color (if (even? depth) "#ffffff" "#fafafa")}}


   ;; [:div
   ;;  {:style {:color "#0a0"}}
   ;;  [muic/Data node]]

   [:div
    {:style {:display :flex}}
    (when-let [text (-> node :graphed.node/side-text)]
      [NodeSideText text])
    [:div {:style {:display :grid :grid-gap spacing}}
     (when-let [text (-> node :graphed.node/name)]
       [NodeName text (-> node :graphed.node/name-type)])
     (when-let [childs (seq (-> node :graphed.node/childs))]
       [NodeChildren buffer-id (inc depth) (-> node :graphed.node/horizontal?)
        (mapv
         (fn [child]
           [Node buffer-id child depth])
         childs)])
     (when-let [childs-ids (seq (-> node :graphed.node/childs-ids))]
       [NodeChildren buffer-id (inc depth) (-> node :graphed.node/horizontal?)
        (mapv
         (fn [child-id]
           [!Node buffer-id child-id depth])
         childs-ids)])]]])


(defn !Node
  [buffer-id node-id depth]
  (let [node @(rf/subscribe [:graphed/buffers-node buffer-id node-id])]
    (if node
      [muic/ErrorBoundary
       [Node buffer-id node depth]]
      [muic/ErrorCard
       "Missing Node: " (pr-str node-id)])))


(defn Buffer [buffer-id]
  [:div
   [:h3 "GraphEd Buffer " (pr-str buffer-id)]
   (if-let [root-node-id @(rf/subscribe [:graphed/buffers-root-node-id buffer-id])]
     [muic/ErrorBoundary
      [!Node buffer-id root-node-id 0]]
     [muic/ErrorCard
      "Missing buffer: " (pr-str buffer-id)])])
