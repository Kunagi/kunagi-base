(ns kunagi-base-browserapp.modules.mf-editor.components.editor
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.api :refer [<subscribe]]
   [mui-commons.theme :as theme]
   [mui-commons.components :as muic]

   [kunagi-base.mf.editor :as editor]))


(defn ActionsMenu []
  [:div "Actions"])


(defn InfoLine []
  [:> mui/Paper
   {:style {:padding (theme/spacing 1)}}
   [:div "Info Line"]])


(declare Node)


(defn NodeList [nodes]
  (into
   [:div
    {:style {:display :grid
             :gird-gap (theme/spacing 0.5)}}]
   (map (fn [node] [Node node]) nodes)))

(defn Node [node]
  [:> mui/Paper
   {:style {:padding (theme/spacing 1)}}
   (-> node :text)
   (when-let [childs (-> node :childs)]
     [NodeList childs])])


(defn View [editor]
  (if-let [root-node (editor/node-tree editor)]
    [:div
     [Node root-node]]
    [:div "no node"]))


(defn Editor [editor]
  [:div
   [:div
    {:style {:display :grid
             :grid-template-columns "auto 300px"
             :grid-gap "1rem"}}
    [muic/ErrorBoundary
     [View editor]]
    [muic/ErrorBoundary
     [ActionsMenu]]]
   [muic/ErrorBoundary
    [InfoLine]]
   [:div
    [:hr]
    [muic/Data editor]]])


(defn Editor! [editor-id]
  (if-let [editor (<subscribe [:mf-editor/editor editor-id])]
    [muic/ErrorBoundary
     [Editor editor]]
    [:div "no editor"]))


(defn Workspace []
  [Editor! :example-editor])
