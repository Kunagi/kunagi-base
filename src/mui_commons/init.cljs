(ns mui-commons.init
  (:require
   [reagent.core :as r]))


(defn install-css [css]
  (let [head (.-head js/document)
        style (.createElement js/document "style")
        text-node (.createTextNode js/document css)]
    (.appendChild style text-node);
    (.appendChild head style)))


(defn install-google-font [font]
  (let [head (.-head js/document)
        link (.createElement js/document "link")
        url (str "https://fonts.googleapis.com/css?family=" font)]
    (set! (.-type link) "text/css")
    (set! (.-rel link) "stylesheet")
    (set! (.-href link) url)
    (.appendChild head link)))


(defn install-roboto-css []
  (install-google-font "Roboto:300,400,500"))


(defn mount-app
  [root-component-f]
  (r/render [root-component-f] (.getElementById js/document "app")))

