(ns html-tools.snippets.browserapp
  (:require
   [html-tools.snippets.preloader :as preloader]
   [cheshire.core :as ceshire]))

(defn js-include [build-name v]
  (str "/" (or build-name "main") ".js"
       (when v
         (str "?v=" v))))


(defn config-script [browserapp-config]
  (let [config-json (ceshire/generate-string {:edn (pr-str browserapp-config)})]
    (str "
  var browserapp_config = " config-json ";
")))


(defn main-script [app-name]
  (when-not app-name (throw (ex-info "app-name required" {})))
  (let [app-name (.replace app-name "-" "_")]
    (str "
  " app-name ".main.init();")))


(defn body []
  [:div {:id "app"}
   preloader/html-code])
