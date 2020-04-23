(ns kcu.mui.datascript
  (:require
   [reagent.core :as r]
   [datascript.core :as d]
   ["@material-ui/core" :as mui]

   [kcu.utils :as u]
   [kcu.mui.output :as output]
   [kcu.devcards :refer [devcard]]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]

   [kcu.mui.table :as table]))


;;; heler ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- q-entities [db & wheres]
  (let [query (into '[:find ?e :where] wheres)]
    (->> (d/q query db)
         (map first)
         (map (partial d/entity db)))))


;;; EntitiesTable ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn EntitiesTable
  [options entities]
  (let [cols (->> entities
                  (map keys)
                  (reduce into #{})
                  sort
                  (map (fn [attr]
                         {:key attr}))
                  (into [{:key :db/id}]))]
    (table/Table
     {:cols cols}
     (->> entities
          (sort-by :db/id)))))


(devcard
 ::EntitiesTable
 [EntitiesTable
  {}
  (let [db (-> (d/empty-db {})
               (d/db-with
                [{:id "1"  :name "Witek"  :age 40 :data "a string"}
                 {:id "2"  :name "Artjom" :age 34 :data 42}
                 {:id "4"  :name "Fabian" :age 37 :data :a-keyword}
                 {:id "25" :name "Kacper" :age 37}]))]
    (q-entities db '[?e :id ?v]))])


;;; QueryingTable ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn DbTable
  [options db]
  (let [STATE (r/atom nil)
        submit (fn [s]
                 (try
                   (->> (str "[" s "]")
                        u/decode-edn
                        (apply q-entities db)
                        (assoc {} :entities)
                        (reset! STATE))
                   (catch :default ex
                     (reset! STATE {:ex ex}))))]
    (fn [options db]
      (let [{:keys [ex entities]} @STATE]
        [muic/Stack-1
         [muic/Inline
          [:> mui/TextField
           {:default-value "[?e]"
            :on-key-down #(when (= 13 (-> % .-keyCode))
                            (submit (-> % .-target .-value)))}]
          [:> mui/Button
           "EDIT"]]
         (when ex
           [muic/ExceptionCard ex])
         (when entities
           [EntitiesTable {} entities])]))))

(devcard
 ::DbTable
 [DbTable
  {}
  (-> (d/empty-db {})
      (d/db-with
       [{:id "1"  :name "Witek"  :age 40 :data "a string"}
        {:id "2"  :name "Artjom" :age 34 :data 42}
        {:id "4"  :name "Fabian" :age 37 :data :a-keyword}
        {:id "25" :name "Kacper" :age 37}]))])
