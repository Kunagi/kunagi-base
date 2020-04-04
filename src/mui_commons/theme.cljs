(ns mui-commons.theme
  (:require
   [goog.object :as gobj]
   ["@material-ui/core/styles" :refer [createMuiTheme]]
   ["@material-ui/core/colors" :as mui-colors]))


(def default-palette
  {:primary {:main (gobj/get (.-blueGrey mui-colors) 700)}
   :secondary {:main (gobj/get (.-green mui-colors) 700)}
   :text-color (gobj/get (.-red mui-colors) 700)

   :greyed "#aaa"})


(def default-theme {:palette default-palette
                    :typography {:useNextVariants true}})


(defn theme->mui-theme [theme]
  (createMuiTheme (clj->js theme)))


(defonce !theme (atom (theme->mui-theme default-theme)))


(defn theme []
  @!theme)


(defn set-theme! [theme]
  (reset! !theme (theme->mui-theme theme))
  ;; (js/console.log "THEME:" (-> theme :palette)))
  (when-let [background-color (-> theme :palette :background :default)]
    (set! (.-backgroundColor (-> js/document.body.style)) background-color)))


;;; helpers

(defn spacing
  ([]
   (spacing 1))
  ([factor]
   (-> (theme) (.spacing factor)))
  ([t r b l]
   (-> (theme) (.spacing t r b l))))

(defn color-background-default []
  (-> @!theme .-palette .-background .-default))

(defn color-primary-main []
  (-> @!theme .-palette .-primary .-main))

(defn color-primary-contrast []
  (-> @!theme .-palette .-primary .-contrastText))

(defn color-secondary-main []
  (-> @!theme .-palette .-secondary .-main))

(defn color-secondary-contrast []
  (-> @!theme .-palette .-secondary .-contrastText))
