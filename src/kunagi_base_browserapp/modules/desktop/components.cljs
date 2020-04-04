(ns kunagi-base-browserapp.modules.desktop.components
  (:require
   [reagent.core :as r]
   ["@material-ui/core" :as mui]
   ["@material-ui/icons" :as icons]

   [mui-commons.api :refer [<subscribe dispatch!]]
   [mui-commons.components :as muic]
   [mui-commons.theme :as theme]))


(defn Error [[id [msg info]]]
  [muic/ErrorCard
   [:div
    {:style {:font-weight :bold}}
    msg]
   [muic/Data info]
   [:br]
   [:> mui/Button
    {:on-click #(dispatch! [:desktop/dismiss-error id])
     :color :inherit}
    "Dismiss"]])


(defn Errors []
  (let [errors (<subscribe [:desktop/errors])]
    (if (empty? errors)
      [:div.NoErrors]
      [:> mui/Container
       {:max-width :md}
       [muic/Stack
        {:items errors
         :template [Error]
         :style {:margin-bottom "1rem"}}]])))


(defn WorkareaSwitch []
  [muic/ErrorBoundary
   (when-let [workarea (<subscribe [:desktop/current-page-workarea])]
     (conj workarea (<subscribe [:desktop/current-page-args])))])


(defn ToolbarSwitch []
  [muic/ErrorBoundary
   (or (<subscribe [:desktop/current-page-toolbar])
       [:div])])


(defn BackButton [on-click]
  [:> mui/IconButton
   {:color :inherit
    :on-click (or on-click
                  #( .back js/window.history))}
   [:> icons/ArrowBack]])


(defn BackButtonSwitch [fallback]
  [muic/ErrorBoundary
   (or (<subscribe [:desktop/current-page-back-button])
       fallback
       [BackButton])])


(defn AppBarToolbar []
  [:div.Toolbar
   {:style {:display :flex}}
   [muic/ErrorBoundary
    [ToolbarSwitch]]])


(defn MainNavIconButtonSwitch [index-page-element]
  (if (= :index (<subscribe [:desktop/current-page-ident]))
    index-page-element
    [BackButtonSwitch]))


(defn DocumentTitleSwitch [suffix]
  (let [title (<subscribe [:desktop/current-page-title-text])
        title (if (fn? title) (title) title)
        title (if suffix
                (if title
                  (if (= suffix title)
                    title
                    (str title " - " suffix))
                  suffix)
                title)]
    (when title
      (set! (. js/document -title) title))
    [:span.DocumentTitleSwitch]))


(defn PageTitleSwitch [variant suffix]
  [:div.PageTitle
   (let [title (<subscribe [:desktop/current-page-title-text])
         title (if (fn? title) (title) title)
         title (if suffix
                 (if title
                   (str title " - " suffix)
                   suffix)
                 title)]
     (when title
       [:> mui/Typography
        {:component :h1
         :variant (or variant :h5)}
        title]))])


(defn Snackbars []
  [:div.Snackbar
   (when-let [snackbar (<subscribe [:desktop/snackbar])]
     [:> mui/Snackbar
      {:open (-> snackbar :open?)
       :message (-> snackbar :message)}])])


(defn Desktop [{:keys [css
                       font-family
                       app-bar
                       container-max-width
                       footer
                       workarea-guard
                       document-title-suffix]}]
  [:div.Desktop
   {:style {:font-family (or font-family "\"Roboto\", \"Helvetica\", \"Arial\", sans-serif")}}
   [DocumentTitleSwitch document-title-suffix]
   [:> mui/CssBaseline]
   [:> mui/MuiThemeProvider
    {:theme (theme/theme)}
    (muic/with-css
      css
      [:div.DesktopContent
       app-bar
       [:div
        {:style {:margin-top (when app-bar "84px")}}
        [Errors]
        [Snackbars]
        [:> mui/Container
         {:max-width container-max-width}
         [muic/ErrorBoundary
          (or workarea-guard
              [WorkareaSwitch])]]]
       footer])]])
