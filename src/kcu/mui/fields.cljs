(ns kcu.mui.fields
  (:require
   ["@material-ui/core" :as mui]

   [kcu.utils :as u]
   [kcu.mui.output :as output]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))



;;; FieldValue ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- value [field]
  (or (get field :value)
      (when-let [attr (get field :attr)]
        (get (get field :entity) attr))))


(defmulti FieldValue (fn [field] (get field :type :text)))


(defmethod FieldValue :text [field]
  (output/Text nil (value field)))


(defmethod FieldValue :text-1 [field]
  [:div (str (value field))])


(defmethod FieldValue :text-n [field]
  [:div
   {:style {:white-space :pre-wrap}}
   (str (value field))])


(defmethod FieldValue :edn [field]
  [muic/Data (value field)])


(defn- Ref [ref]
  [:> mui/Button
   {:on-click (get ref :on-click)
    :href (get ref :href)
    :size :small
    :variant :contained
    :style {:text-transform :none}}
   (or (get ref :text)
       (str "? " ref))])


(defmethod FieldValue :ref-n [field]
  [muic/Inline
   {:items (value field)
    :template [Ref]}])


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


(defn Field [field]
  [:div.Field
   {:style {:cursor (when (-> field :on-click) :pointer)}
    :on-click (-> field :on-click)}
   [FieldLabel (field-label field)]
   [:div.Field__Value
    {:style {:min-height "24px"}}
    (or (get field :value-component)
        [FieldValue field])]])


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
