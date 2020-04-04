(ns mui-commons.nested-paperscraps
  (:require
   ["@material-ui/core" :as mui]
   [re-frame.core :as rf]))

(def spacer-size 5)
(def spacer-unit "px")


(defn deep-merge [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))


(defn spacer [count]
  (str (* count spacer-size) spacer-unit))


(declare component-for-value)


(defn Paperscrap
  [options content]
  [:> mui/Paper
   (deep-merge
    {:style {:padding (spacer 1)
             :margin (spacer 1)}}
    options)
   content])


(defn Map-Element
  [[k v]]
  [Paperscrap
   {}
   [:div
    {:style {:display :flex
             :flex-wrap :wrap
             :align-items :flex-start}}
    [:div
     (component-for-value k)]
    [:div
     {:style {:line-height "230%"}}
     "→"]
    (component-for-value v)]])


(defn Map
  [m]
  [Paperscrap
   {}
   [:div
    {:style {:display :flex
             :flex-wrap :wrap
             :align-items :flex-start}}
    [:div
     {:style {:margin-right "5px"}}
     "{"]
    [:div
     (into
      [:div]
      (map
       (fn [entry]
         [Map-Element entry])
       m))]]])


(defn Sequence_
  [identifier s]
  [Paperscrap
   {}
   [:div
    {:style {:display :flex}}
    [:div
     {:style {:margin-right "5px"}}
     identifier]
    (into
     [:div
      {:style {:display :flex
               :flex-wrap :wrap
               :align-items :flex-start}}]
     (map
      (fn [entry]
        (component-for-value entry))
      s))]])


(defn Primitive
  [options v]
  [Paperscrap
   (deep-merge
    options
    {:style {:font-weight :normal}})
   (str v)])


(defn component-for-value
  [v]
  (cond
    (nil? v) [Primitive {} "nil"]
    (keyword? v) [Primitive {} v]
    (string? v) [Primitive {} v]
    (number? v) [Primitive {} v]
    (symbol? v) [Primitive {} v]
    (map? v) [Map v]
    (vector? v) [Sequence_ "[" v]
    (set? v) [Sequence_ "#{" v]
    (seq? v) [Sequence_ "(" v]
    :else [Primitive {:style {:background-color :red}} v]))


(defn Data
  [data]
  (component-for-value data))


(defn ExampleData
  []
  [Data '(hello world {:with :a
                       :vector [:vector :items :here 1 2 3 "hello"]
                       :set #{"x" a :b 3}
                       :empty-vector []
                       :empty-map {}
                       :nil nil
                       [:a :more "complex key" {:with :data}] :value})])

;;;

;; (defn optional-Title
;;   [title]
;;   (when title
;;     [:div title]))


;; (defn optional-DoubleTitle
;;   [a b]
;;   (when a
;;     [:div
;;      [:span
;;       {:style {:font-weight 500}}
;;       a]
;;      (when b
;;       [:span
;;        {:style {:font-weight 100}}
;;        " | "
;;        b])]))


;; (defn Paper
;;   [{:as options
;;     :keys [title
;;            title-suffix]}
;;    & components]
;;   (into
;;    [:> mui/Paper
;;     {:style {:padding (spacer 1)}}]
;;    (into
;;     [(optional-DoubleTitle title title-suffix)
;;      (when (and title (not-empty components)) [:div {:style {:margin-bottom (spacer 1)}}])]
;;     components)))
