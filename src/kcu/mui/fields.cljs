(ns kcu.mui.fields
  (:require
   ["@material-ui/core" :as mui]

   [kcu.utils :as u]
   [kcu.mui.output :as output]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))


;;; FieldLabel ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- field-label [field]
  (or (or (get field :label)
          (u/humanize-label (get field :attr)))
      "?missing :label"))


(defn FieldLabel [text]
  [:div.Field__Label
   {:style {:color (theme/color-primary-main)
            :text-transform :uppercase
            :letter-spacing "1px"
            :font-size "70%"}}
   text])


;;; Field ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- value [field]
  (or (get field :value)
      (when-let [attr (get field :attr)]
        (get (get field :entity) attr))))


(defn Field
  "Renders a Field (Label + Value)"
  [field]
  [:div.Field
   {:style {:cursor (when (-> field :on-click) :pointer)}
    :on-click (-> field :on-click)}
   [FieldLabel (field-label field)]
   [:div.Field__Value
    {:style {:min-height "24px"}}
    (or (get field :value-component)
        (output/output (assoc field
                              :value (value field))))]])


;;; Fieldset ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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
