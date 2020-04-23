(ns kcu.mui.devcards
  (:require
   ["@material-ui/core" :as mui]

   [kunagi-base.appmodel :refer [def-module]]
   [kunagi-base-browserapp.modules.desktop.model :refer [def-page]]

   [kcu.butils :as bu]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]
   [kcu.devcards :as devcards]))




;;; Devcard ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Devcard [options]
  [muic/Card
   [muic/Stack-1
    [:div
     {:style {:color (theme/color-primary-main)
              :font-weight :bold
              :letter-spacing "1px"}}
     (-> options :name str)
     " "
     [:span
      {:style {:font-weight :normal
               :color "#666"}}
      (-> options :ns name str)]]
    [:div
     {:style {:display :flex
              :flex-wrap :wrap}}
     [:div
      {:style {:border "1px dotted #9f9"}}
      [muic/ErrorBoundary
       (-> options :component)]]]
    (when-let [code (-> options :code)]
      [muic/Card
       {:style {:background-color "#00363a"
                :color :white}}
       [muic/Data code]])]])


(defn DevcardsGroup [group]
  (into
   [muic/Stack-1]
   (map (fn [devcard]
          [muic/ErrorBoundary
           [Devcard devcard]])
        (->> (devcards/devcards)
             (filter #(= group (get % :ns)))
             (sort-by :id)
             reverse))))


;;; Navigations ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn NavigationSidebar []
  (into
   [muic/Stack-1]
   (for [group (->> (devcards/devcards)
                    (map :ns)
                    (into #{})
                    sort)]
     ^{:key group}
     [:> mui/Link
      {:href (str "devcards?group=" (bu/url-encode (name group)))}
      group])))


;;; integration ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn Workarea [args]
  (let [group-name (get args :group)]
    [:div
     {:style {:display :grid
              :grid-template-columns "180px auto"
              :grid-gap (theme/spacing)}}
     [:div
      [NavigationSidebar]]
     [:div
      (when group-name
        [muic/ErrorBoundary
         [DevcardsGroup (keyword group-name)]])]]))


(def-module
  {:module/id ::devcards})


(def-page
  {:page/id ::devcards
   :page/ident :devcards
   :page/title-text "Dev Cards"
   :page/workarea [(fn [args] [Workarea args])]})
