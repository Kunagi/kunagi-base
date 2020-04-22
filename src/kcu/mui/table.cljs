(ns kcu.mui.table
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]

   [kcu.utils :as u]
   [kcu.mui.output :as output]
   [kcu.devcards :refer [devcard]]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))


(defn Table
  [options records]
  (let [cols (get options :cols)]
    [:div.Table
     [:> mui/Table
      {:size :small}
      [:> mui/TableHead
       [:> mui/TableRow
        (for [col cols]
          (let [k (get col :key)
                label (get col :label)]
            ^{:key k}
            [(r/adapt-react-class mui/TableCell)
             {:style {:color (theme/color-primary-main)}}
             label]))]]
      [:> mui/TableBody
       (for [record records]
         ^{:key (-> record)}
         [(r/adapt-react-class mui/TableRow)
          {:hover true
           :on-click (when-let [on-click (get options :on-click)]
                       #(on-click {:record record}))}
          (for [col cols]
            (let [k (get col :key)
                  value (get record k)
                  value-type (get col :type)]
              ^{:key k}
              [(r/adapt-react-class mui/TableCell)
               (output/output
                {:type value-type
                 :value value})]))])]]]))


(devcard
 ::Table
 [Table
  {:cols [{:key :name
           :label "Name"}
          {:key :age
           :label "Age"}
          {:key :data
           :label "Data"
           :type :edn}]}
  [{:id "1"  :name "Witek"  :age 40 :data "a string"}
   {:id "2"  :name "Artjom" :age 34 :data 42}
   {:id "4"  :name "Fabian" :age 37 :data :a-keyword}
   {:id "25" :name "Kacper" :age 37}]])
