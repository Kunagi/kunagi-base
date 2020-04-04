(ns mui-commons.fieldset)


(def field-spacing "1.2rem")


(defn conform-label [label]
  (if (string? label)
    {:text label}
    label))

(defn Label [& {:as options :keys [text]}]
  [:div
   {:style {:color "#a1a8c3"
            :font-weight 400
            :font-size "13px"}}
   text])


(defn make-clickable [predicate? on-click component]
  (if-not predicate?
    component
    [:div
     {:on-click on-click
      :style {:cursor :pointer}}
     component]))


(defn Field [& {:as options :keys [label
                                   value
                                   on-click]}]
  (make-clickable
   on-click
   on-click
   [:div.Fieldset--Field
    {:style {:margin-bottom field-spacing
             :margin-right field-spacing}}
             ;;:background-color "#f7f8fa"
             ;;:padding "0.5rem"
             ;;:border-radius "5px"}}
    (into [Label] (apply concat (conform-label label)))
    [:div
     {:style {:margin-top "0.3rem"}}
     value]]))


(defn Row [& {:as options :keys [fields]}]
  (into [:div.Fieldset--Row
         {:style {:display :flex
                  :flex-direction :row
                  :width "100%"}}]
        (mapv (fn [field]
                [:div
                 {:style {:flex-grow 1}}
                 (into [Field] (apply concat field))])
              fields)))


(defn Fieldset [& {:as options :keys [rows]}]
  (into [:div.Fieldset]
        (mapv (fn [row]
                (into [Row] (apply concat row)))
              rows)))
