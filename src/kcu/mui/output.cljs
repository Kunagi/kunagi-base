(ns kcu.mui.output
  (:require
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [kcu.utils :as u]
   [kcu.devcards :refer [devcard]]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))


(defn Text [options value]
  [:div.Output.Output-Text (str value)])

(devcard ::Text [Text nil "Hello World"])



(defn Text-n [options value]
  [:div.Output.Output-Text
   {:style {:white-space :pre-wrap}}
   (str value)])

(devcard ::Text-n [Text-n nil "Hello\nWorld"])


(defn Code [options value]
  [:div.Output.Output-Code
   {:style {:white-space :pre-wrap
            :font-family :monospace}}
   (str value)])

(devcard ::Code [Code nil "<ul>\n  <li>item 1</li>\n<ul>"])


(defn Edn [options value]
  [:div.Output.Output-Edn
   [muic/Data value]])

(devcard ::Edn [Edn nil {:this :is :some ["EDN" 'data]}])


(defn RefButton
  [options
   {:keys [text id on-click href]}]
  [:div.Output.Output-RefButton
   [:> mui/Button
    {:on-click on-click
     :href href
     :size :small
     :variant :contained
     :style {:text-transform :none}}
    (or text
        (str "? " id))]])

(devcard
 ::RefButton
 [RefButton nil {:on-click #(js/console.log "Ref clicked") :text "something else"}])


(defn RefsButtons
  [options
   refs]
  [:div.Output.Output-RefsButtons
   (into
    [muic/Inline]
    (-> []
        (into
         (map (fn [{:keys [text id on-click href]}]
                [:> mui/Button
                 {:on-click on-click
                  :href href
                  :size :small
                  :variant :contained
                  :style {:text-transform :none}}
                 (or text
                     (str "? " id))])
              refs))
        (conj
         (when-let [on-add (get options :on-add)]
           [:> mui/IconButton
            {:on-click on-add
             :size :small
             :color :primary
             :style {:text-transform :none}}
            [:> icons/Add]]))))])

(devcard
 ::RefsButtons
 [RefsButtons
  {:on-add #(js/console.log "add")}
  [{:on-click #(js/console.log "Ref #1 clicked") :text "Ref #1"}
   {:href "/ui/devcards" :text "Ref #2"}
   {:on-click #(js/console.log "Ref #3 clicked") :text "Ref #3"}]])



;;; data based dispatching ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn output [{:as options :keys [type value]}]
  (case type
    :text (Text options value)
    :text-1 (Text options value)
    :text-n (Text-n options value)
    :code (Code options value)
    :edn (Edn options value)
    :ref (RefButton options value)
    :ref-n (RefsButtons options value)
    (Edn options value)))
