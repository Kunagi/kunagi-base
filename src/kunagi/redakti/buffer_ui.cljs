(ns kunagi.redakti.buffer-ui
  (:require
   [clojure.spec.alpha :as s]
   ["@material-ui/core" :as mui]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]
   [kunagi.redakti.buffer :as buffer]))


(declare node)

(def spacing "5px")
(def palette
  {:cursor "#900"
   :node {:background-color "#222"}
   :container {:background-color "#111"}})
(def cursor-outline (str "1px solid " (-> palette :cursor)))
(def cursor-box-shadow "0px 2px 1px -1px rgba(200,0,0,0.2), 0px 1px 1px 0px rgba(200,0,0,0.14), 0px 1px 3px 0px rgba(200,0,0,0.12)")
(def cursor-background-color (-> (theme/theme) .-palette .-secondary .-light)) ; "#a5d6a7")

(defn NodeFrame [options buffer n path component]
  [:> mui/Paper
   {:style {:padding spacing
            :background-color (when (= path (-> buffer :cursor)) (-> (theme/theme) .-palette .-secondary .-light))}}
   [muic/Data [path (-> buffer :cursor)]]
   ;;{:style {:outline (when (= path (-> buffer :cursor)) cursor-outline)}}
   ;; :background (-> palette :node :background-color)
   ;; :padding spacing}}
   component])


(defn Leaf [buffer n path]
  ;;[:div.Leaf]
  [NodeFrame {} buffer n path
   [:div
    (when-let [text (-> n :redakti.node/text)]
      [:div.Leaf-Text
       text])]])
    ;; [:div.Debug
    ;;  [muic/Data path]
    ;;  [muic/Data n]]])



(defn Column [buffer n path]
  [NodeFrame {} buffer n path
   [:div
    ;; [muic/Data path]
    (when-let [text (-> n :redakti.node/text)]
      [:div.Leaf-Text
       {:style {:padding-bottom spacing}}
       text])
    (into
     [muic/Stack
      {:spacing spacing}]
       ;; :style {:padding spacing}}]
               ;;:background-color (-> palette :container :background-color)]
     (map-indexed
      (fn [idx _child-n]
        (node buffer (conj path idx)))
      (-> n :redakti.node/nodes)))]])


(defn Row [buffer n path]
  [:div "row"])


(defn node [buffer path]
  (let [tree (-> buffer :tree)
        n (buffer/node-by-path tree path)]
    ;; (s/assert keyword? (-> n :redakti.node/type))
    ;; (js/console.log "XXX" path n)
    (case (-> n :redakti.node/type)
      :column [Column buffer n path]
      :row [Row buffer n path]
      :leaf [Leaf buffer n path]
      (throw (ex-info (str "Unsupported node type: " (-> n :redakti.node/type))
                      {:path path
                       :node n})))))


(defn Buffer [buffer]
  [muic/ErrorBoundary
   [:div.Buffer
    {:style {:padding spacing}}
    (js/console.log "BUFFER" (-> buffer :tree))
    (node buffer [])]])
    ;; (node {:cursor []
    ;;        :tree {:redakti.node/type :column
    ;;               :redakti.node/text "root"
    ;;               :redakti.node/nodes [{:redakti.node/text "c1"
    ;;                                     :redakti.node/type :column}]}}
    ;;       [])]])
