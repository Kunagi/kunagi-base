(ns kcu.mui.fields
  (:require
   ["@material-ui/core" :as mui]

   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))



(defn Field [field]
  [:div.Field
   {:style {:cursor :pointer}
    :on-click (-> field :on-click)}
   [:div.Field__Label
    {:style {:color (theme/color-primary-main)
             :text-transform :uppercase
             :letter-spacing "1px"
             :font-size "80%"}}
    (-> field :label)]
   [:div.Field__Value
    (-> field :value)]])


(defn Fieldset [options & fields]
  [:div.Fieldset
   (into
    [muic/Stack
     {:spacing (theme/spacing 2)}]
    fields)])
