(ns kcu.mui.table
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]

   [kcu.utils :as u]
   [kcu.mui.output :as output]
   [kcu.devcards :refer [devcard]]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))



(defn TableHead [options]
  [:> mui/TableHead
   [:> mui/TableRow
    (when (get options :selection-mode)
      [:> mui/TableCell])
    (for [col (get options :cols)]
      (let [k (get col :key)
            label (or (get col :label)
                      (str k))]
        ^{:key k}
        [(r/adapt-react-class mui/TableCell)
         {:style {:color (theme/color-primary-main)}}
         label]))]])


(defn record-cell [options record col]
  (let [k (get col :key)
        value (get record k)
        value-type (get col :type)]
    ^{:key k}
    [(r/adapt-react-class mui/TableCell)
     (output/output
      {:type value-type
       :value value})]))


(defn record-id [options record]
  (get record :id))

(defn- toggle-selection [STATE options record]
  (swap! STATE
         (fn [state]
           (let [selected (get state :selected)
                 id (record-id options record)
                 selected (if (= :one (get options :selection-mode))
                            (if (contains? selected id)
                              #{}
                              #{id})
                            (if (contains? selected id)
                              (disj selected id)
                              (conj selected id)))]
             (when-let [select-input-id (get options :selection-input-id)]
               (set! (-> (js/document.getElementById select-input-id) .-value)
                     (u/encode-edn selected)))
             (assoc state :selected selected)))))


(defn- record-selected? [state options record]
  (-> state :selected (contains? (record-id options record))))


(defn record-row [STATE options record]
  (let [selection-mode (get options :selection-mode)
        selected? (when selection-mode (get record ::selected?))
        on-click (or (when selection-mode
                       #(toggle-selection STATE options record))
                     (when-let [on-click (get options :on-click)]
                       #(on-click {:record record})))]
    ^{:key (-> record)}
    [(r/adapt-react-class mui/TableRow)
     {:hover true
      :role (when selection-mode "checkbox")
      :selected selected?
      :on-click on-click
      :style {:cursor (when selection-mode :pointer)}}
     (when selection-mode
       [(r/adapt-react-class mui/TableCell)
        {:padding "checkbox"}
        [:> mui/Checkbox
         {:checked selected?}]])
     (for [col (get options :cols)]
       (record-cell options record col))]))


(defn Table
  [options records]
  ;; TODO assert :selected is set
  (let [STATE (r/atom {:selected (if-let [selected (get options :selected)]
                                    (into #{} selected)
                                    #{})})]
    (fn [options records]
      (let [state @STATE
            records (map (fn [record]
                           (assoc record
                                  ::selected?
                                  (record-selected? state options record)))
                         records)]
        [:div
         ;; [muic/Data {:state @STATE
         ;;             :options options}]
         (when-let [select-input-id (get options :selection-input-id)]
           [:input
            {:id select-input-id
             "data-value-is-edn" true
             :type :hidden
             :value (u/encode-edn (get state :selected))}])
         [:div.Table
          [:> mui/Table
           {:size :small}

           [TableHead options]

           [:> mui/TableBody
            (for [record records]
              (record-row STATE options record))]]]]))))


(devcard
 ::Table
 [Table
  {:cols [{:key :name
           :label "Name"
           :type :text}
          {:key :age}
          {:key :data
           :label "Data"
           :type :edn}]}
  [{:id "1"  :name "Witek"  :age 40 :data "a string"}
   {:id "2"  :name "Artjom" :age 34 :data 42}
   {:id "4"  :name "Fabian" :age 37 :data :a-keyword}
   {:id "25" :name "Kacper" :age 37}]])


(devcard
 ::Table-selection-mode-many
 [Table
  {:selection-mode :many
   :selected #{"1" "4"}
   :cols [{:key :name
           :label "Name"
           :type :text}
          {:key :age}
          {:key :data
           :label "Data"
           :type :edn}]}
  [{:id "1"  :name "Witek"  :age 40 :data "a string"}
   {:id "2"  :name "Artjom" :age 34 :data 42}
   {:id "4"  :name "Fabian" :age 37 :data :a-keyword}
   {:id "25" :name "Kacper" :age 37}]])


(devcard
 ::Table-selection-mode-one
 [Table
  {:selection-mode :one
   :selected #{"4"}
   :cols [{:key :name
           :label "Name"
           :type :text}
          {:key :age}
          {:key :data
           :label "Data"
           :type :edn}]}
  [{:id "1"  :name "Witek"  :age 40 :data "a string"}
   {:id "2"  :name "Artjom" :age 34 :data 42}
   {:id "4"  :name "Fabian" :age 37 :data :a-keyword}
   {:id "25" :name "Kacper" :age 37}]])
