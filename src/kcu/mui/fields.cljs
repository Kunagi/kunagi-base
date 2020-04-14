(ns kcu.mui.fields
  (:require
   ["@material-ui/core" :as mui]

   [kcu.utils :as u]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))



(defn- field-label [field]
  (or (get field :label)
      (u/humanize-label (get field :attr))))


(defn- field-value [field]
  (or (get field :value-component)
      (get field :value)
      (when-let [attr (get field :attr)]
        (get (get field :entity) attr))))


(defn Field [field]
  [:div.Field
   {:style {:cursor :pointer}
    :on-click (-> field :on-click)}
   [:div.Field__Label
    {:style {:color (theme/color-primary-main)
             :text-transform :uppercase
             :letter-spacing "1px"
             :font-size "70%"}}
    (field-label field)]
   [:div.Field__Value
    (field-value field)]])



(defn Fieldset [options & components]
  (let [entity (get options :entity)
        field-on-click (get options :field-on-click)
        fields (map (fn [field]
                      [Field
                       (cond-> field

                         (and entity (not (contains? field :entity)))
                         (assoc :entity entity)

                         field-on-click
                         (assoc :on-click #(field-on-click field)))])

                    (get options :fields))]
    [:div.Fieldset
     (into
      [muic/Stack
       {:spacing (theme/spacing 2)}]
      (concat
       fields
       components))]))
