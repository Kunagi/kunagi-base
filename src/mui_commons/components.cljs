(ns mui-commons.components
  (:require
   [cljs.pprint :as pprint]
   [reagent.core :as r]
   [reagent.dom :as rdom]
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]
   ["@material-ui/core/styles" :refer [withStyles]]

   [kcu.config :as config]
   [mui-commons.theme :as theme]
   [mui-commons.api :refer [<subscribe]]
   [clojure.string :as str]))


(defn- deep-merge [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))


(defn with-css [css component]
  [:> ((withStyles (fn [theme]
                     (clj->js {:root
                               (if (fn? css)
                                 (css theme)
                                 css)})))
       (r/reactify-component
        (fn [{:keys [classes ] :as props}]
          [:div
           {:class (.-root classes)}
           component])))])


(defn HTML [html-code]
  [:div.HTML
   {:dangerouslySetInnerHTML {:__html html-code}}])


(defn Data
  [& datas]
  (into
   [:div.Data
    {:style {:display :grid
             :grid-gap "10px"}}]
   (map (fn [data]
          [:code
           {:style {:white-space :pre-wrap
                    :overflow :auto}}
           (with-out-str (pprint/pprint data))])
        datas)))


(defn None []
  [:div {:style {:display :none}}])


(defn- stack->hiccup [stack]
  (reduce (fn [div line]
            (conj div
                  [:div
                   {:style {:color (when (or
                                          (-> line
                                              (str/includes? "cljs.core.js"))
                                          (-> line
                                              (str/includes? "react_dom_development.js"))
                                          (-> line
                                              (str/includes? "reagent.impl.component.js")))
                                     "#aaa")}}
                   line]))
          [:div]
          (-> stack (.split "\n"))))

(defn StackTrace [stack]
  (when stack
    [:div
     {:style {:font-family :monospace
              :white-space :pre-wrap
              :padding "1rem 0"}}
     (stack->hiccup
      (if (-> stack (.startsWith "\n"))
        (-> stack (.substring 1))
        stack))]))


(defn Exception [exception]
  (let [message (.-message exception)
        message (if message message (str exception))
        data (ex-data exception)
        cause (or (ex-cause exception) (.-cause ^js exception))
        stack (.-stack exception)]
    [:div.Exception
     (when cause
       [:div
        [Exception cause]
        [:div
         {:style {:margin "1rem 0"
                  :color :lightgrey
                  :font-style :italic}}
         "causes"]])
     ;; [:> mui/Card
     ;;  (js/console.log exception)
     ;;  [Data (-> exception)]]
     [:div
      {:style {:font-weight :bold}}
      (str message)]
     [StackTrace stack]
     (when-not (empty? data)
       (if (= ::error-boundary (-> data :error-type))
         [:div
          [StackTrace (get-in data [:info "componentStack"])]
          [Data (update data :info :dissoc "componentStack")]]
         [Data data]))]))


(defn ErrorCard [& contents]
  [:> mui/Card
   {:style {:background-color "#b71c1c" ; red 900
            :color "#ffffff"
            :min-width "400px"}}
   [:> mui/CardContent
    [:div
     {:style {:display :flex
              :overflow-x :auto}}
     [:> icons/BugReport
      {:style {:margin-right "1rem"}}]
     (into [:div] contents)]]])


(defn ExceptionCard [exception]
  [ErrorCard
    (if exception
      [:div
       {:style {:margin "1em"}}
       [Exception exception]]
      [:div "A bug is making trouble :-("])])


(defn ErrorBoundary [comp]
  (if-not comp
    [:span]
    (let [!exception (r/atom nil)]
      (r/create-class
       {:component-did-catch
        (fn [this ex info]
          (let [cljs-react-class (-> comp first .-cljsReactClass)
                comp-name (if cljs-react-class
                            (-> cljs-react-class .-displayName)
                            "no-name")]
            (js/console.log
             "ErrorBoundary"
             "\nthis:" this
             "\ncomp" comp
             "\ncomp-name" comp-name
             "\nex:" ex
             "\ninfo:" info)
            (reset! !exception (ex-info (str "Broken component: " comp-name)
                                        {:error-type ::error-boundary
                                         :component comp
                                         :info (js->clj info)
                                         :react-class cljs-react-class}
                                        ex))))
        :reagent-render (fn [comp]
                          (if-let [exception @!exception]
                            [ExceptionCard exception]
                            comp))}))))


;;; simple helper components


(defn ForeignLink
  [options & contents]
  (into
   [:> mui/Link
    (deep-merge
     {:target :_blank
      :rel :noopener
      :color :inherit}
     options)]
   (if (empty? contents)
     [(get options :href)]
     contents)))


(defn args-as-options [args]
  (if (empty? args)
    nil
    (let [options (first args)]
      (if (map? options)
        (update options :elements #(if %
                                     (into % (rest args))
                                     (rest args)))
        {:elements args}))))


(defn ScrollTo [{:keys [offset]} content]
  (let [!id (r/atom (str "scroll_" (random-uuid)))]
    (r/create-class
     {:reagent-render
      (fn [offset content]
        [:div {:id @!id}
         content])
      :component-did-mount
      (fn []
        (-> (js/document.getElementById @!id) .scrollIntoView)
        (when offset
          (-> (js/scrollBy 0 (- 0 offset)))))})))


(defn Focusable [& args]
  (let [!id (r/atom (str "focusable_" (random-uuid)))]
    (r/create-class
     {:reagent-render
      (fn [& args]
        (let [options  (args-as-options args)
              elements (-> options :elements)
              options  (dissoc options :elements)]
          (into
           [:div.Focusable
            (assoc options
                   :id @!id
                   :tabIndex 1)]
           elements)))

      :component-did-mount
      #(-> (js/document.getElementById @!id) .focus)})))


;;; Text

(defn Text [& optmap+elements]
  (let [options (args-as-options optmap+elements)
        {:keys [elements
                size]} options]
    (into
     [:div.Text]

     elements)))

;;; Layouts


(defn Stack [& optmap+elements]
  (let [options (args-as-options optmap+elements)
        {:keys [spacing
                elements
                items
                template]} options
        options (dissoc options :spacing :elements :items :template)
        spacing (or spacing
                    (theme/spacing 0.5))
        elements (if-not items
                   elements
                   (concat elements
                           (let [item-template (or template [:span])]
                             (map #(conj item-template %) items))))]
    (into
     [:div.Stack
      (deep-merge options
                  {:style {:display :grid
                           :grid-template-columns "100%"
                           :grid-gap spacing}})]
     elements)))

(defn Stack-1 [& elements]
  (Stack {:spacing (theme/spacing 1)
          :elements elements}))


(defn Inline [& optmap+elements]
  (let [options (args-as-options optmap+elements)
        {:keys [spacing
                elements
                items
                template]} options
        spacing (or spacing
                    (theme/spacing 0.5))
        elements (if-not items
                   elements
                   (concat elements
                           (let [item-template (or template [:span])]
                             (map #(conj item-template %) items))))]
    (into
     [:div.Inline
      (deep-merge
       (dissoc options :elements :spacing :items :template)
       options
       {:style {:display :flex
                :flex-wrap :wrap
                :margin (str "-" spacing "px")}})]
     (map
      (fn [element]
        [:div
         {:style {:margin (str spacing "px")}}
         element])
      elements))))


(defn TitledInline [& optmap+elements]
  (let [options (args-as-options optmap+elements)
        {:keys [title
                stack-options
                title-options]} options]
    [Stack
     stack-options
     [Text title-options title]
     [Inline (dissoc options :title :stack-options :title-options)]]))


;;; Cards


(defn Card [& args]
  (let [options (args-as-options args)
        title (-> options :title)
        padding (or (-> options :style :padding)
                    (theme/spacing 2))
        options (assoc-in options [:style :padding] padding)
        elements (-> options :elements)
        options (dissoc options :elements)
        spacing (get options :spacing)
        options (dissoc options :spacing)]
    [:> mui/Paper
     options
     (when title
       [:div.title
        {:style {:font-weight :bold}}
        title])
     (into
      [Stack
       {:spacing spacing}]
      elements)]))


(defn ActionCard
  [options & children]
  [:> mui/Card
   [:> mui/CardActionArea
    (select-keys options [:href :on-click])
    (into
     [:> mui/CardContent]
     children)]])


(defn DataCard
  [datas]
  [Card
   (into [Data] datas)])


;;; DropdownMenu


(defn DropdownMenu
  [options menu-items-f]
  (let [!anchor-el (atom nil)
        !open? (r/atom false)]
    (fn [{:keys [button-text
                 button-icon
                 style]}
         & menu-items]
      [:div
       {:style style}
       [:> (if button-text mui/Button mui/IconButton)
        {:color :inherit
         :on-click #(reset! !open? true)
         :ref #(reset! !anchor-el %)}
        button-icon
        button-text
        (when button-text
          [:> icons/ArrowDropDown])]
       (into
        [:> mui/Menu
         {:open (-> @!open?)
          :anchor-el @!anchor-el
          :keep-mounted true
          :on-close #(reset! !open? false)}]
        (menu-items-f #(reset! !open? false)))])))


;;; progress boundary

(defn- asset-error? [resource]
  (and (vector? resource)
       (= :asset/error (first resource))))

(defn DataProgressBoundary [data component height]
  (if data
    (if (= :auth/not-permitted data)
      [ErrorCard "Access denied"]
      [ErrorBoundary
       (if (fn? component)
         [component data]
         (conj component data))])
    [:div
     {:style {:display :grid
              :justify-content :center
              :align-content :center
              :min-height (when height height)}}
     [:> mui/CircularProgress]]))


(defn SubscriptionProgressBoundary [subscription component height]
  [DataProgressBoundary
   (<subscribe subscription)
   component
   height])


;;; Accordion from mui/ExpansionPanel


(defn- AccordionExpansionPanel [!expanded id item summary-f details-f]
  (let [expanded? (= id @!expanded)]
    [:> mui/ExpansionPanel
     {:expanded expanded?
      :on-change (fn [_ expanded?] (reset! !expanded (when expanded? id)))}
     [:> mui/ExpansionPanelSummary
      [:span
       (summary-f item)]]
     (when expanded?
       [:> mui/ExpansionPanelSummary
        (details-f item)])]))


(defn Accordion [items summary-f details-f]
  (let [!expanded (r/atom nil)]
    (fn [items summary-f details-f]
      (into
       [:div.Accordion]
       (map-indexed
        (fn [idx item]
          [AccordionExpansionPanel !expanded idx item summary-f details-f])
        items)))))


;;; ExpansionPanels mui/ExpansionPanel


(defn- ExpansionPanel [!expandeds id item summary-f details-f]
  (let [expanded? (contains? @!expandeds id)]
    [:> mui/ExpansionPanel
     {:expanded expanded?
      :on-change (fn [_ expanded?] (swap! !expandeds (fn [expandeds]
                                                       (if expanded?
                                                         (conj expandeds id)
                                                         (disj expandeds id)))))}
     [:> mui/ExpansionPanelSummary
      [:span
       (summary-f item)]]
     (when expanded?
       [:> mui/ExpansionPanelSummary
        (details-f item)])]))


(defn ExpansionPanels [items summary-f details-f]
  (let [!expanded (r/atom #{})]
    (fn [items summary-f details-f]
      (into
       [:div.Accordion]
       (map-indexed
        (fn [idx item]
          [ExpansionPanel !expanded idx item summary-f details-f])
        items)))))


;;; width aware wrapper

(defn WrapWidthAware [width-aware-component]
  (let [!width (r/atom nil)]
    (r/create-class
     {:reagent-render
      (fn [] (conj width-aware-component @!width))
      :component-did-mount
      #(let [node (-> % rdom/dom-node)
             width (.-offsetWidth node)]
         (when-not (= width @!width)
           (reset! !width width))
         (when (exists? js/ResizeObserver)
           (-> (js/ResizeObserver. (fn []
                                     (let [width (.-offsetWidth node)]
                                       (when-not (= width @!width)
                                         (reset! !width width)))))
               (.observe node))))})))


(defn WidthAwareBreakepointsWrapper [breakepoints width-aware-component width]
  (conj
   width-aware-component
   (reduce
    (fn [ret breakepoint]
      (if (>= width breakepoint) breakepoint ret))
    0
    breakepoints)))

;;; Table

(defn Table [{:keys [data cols]}]
  [:> mui/Table
   [:> mui/TableHead
    (into
     [:> mui/TableRow]
     (map
      (fn [col]
        [:> mui/TableCell
         (-> col :head-text)])
      cols))]
   (into
    [:> mui/TableBody]
    (map
     (fn [record]
       (into
        [:> mui/TableRow]
         ;; [:> mui/TableCell
         ;;  [Data cols]]]))
        (map
         (fn [col]
           [:> mui/TableCell
            ((-> col :value) record)])
         cols)))
     data))])


;;; Tabs




;;; dialogs

(defn ClosableDialog [!open? title content]
  [:> mui/Dialog
   {:open @!open?
    :on-close #(reset! !open? false)}
   [:> mui/DialogTitle
    title
    [:> mui/IconButton
     {:on-click #(reset! !open? false)
      :style {:position :absolute
              :right (theme/spacing)
              :top (theme/spacing)}}
     [:> icons/Close]]]
   [:> mui/DialogContent
    content]])


;;; dev mode ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def dev-mode? (-> (config/config) :dev-mode?))


(defn DevGuard [dev-content]
  (if dev-mode?
    dev-content
    [:div]))


(defn DataInspector [& datas]
  (let [OPEN? (r/atom false)]
    (fn [& datas]
      [:div
       [:> mui/IconButton
        {:on-click #(reset! OPEN? true)
         :size :small}
        [:> icons/Memory]]
       (when @OPEN?
        [:> mui/Dialog
         {:open true
          :on-close #(reset! OPEN? false)}
         [:> mui/DialogContent
          (into [Data] datas)]])])))


(defn WithData [data component]
  (let [component (conj component data)
        component [ErrorBoundary component]]
    (if-not dev-mode?
      component
      [:div
       {:style {:position :relative}}
       component
       [:div
        {:style {:position :absolute
                 :top 0
                 :right 0}}
        [DataInspector data]]])))
