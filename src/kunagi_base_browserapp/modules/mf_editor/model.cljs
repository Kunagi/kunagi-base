(ns kunagi-base-browserapp.modules.mf-editor.model
  (:require
   [kunagi-base.appmodel :as am :refer [def-module]]
   [kunagi-base.modules.startup.model :refer [def-init-function]]

   [kunagi-base-browserapp.modules.mf-editor.reframe]

   [kunagi-base.mf.editor :as editor]
   [kunagi-base.mf.extensions.root :refer [new-root-extension]]))


(def-module
  {:module/id ::mf-editor})


(defn- create-example-editor []
  (-> (editor/new-editor)
      (editor/add-extension (new-root-extension))
      (editor/activate-view :root :entities)))


(def-init-function
  {:init-function/id ::start
   :init-function/module [:module/ident :mf-editor]
   :init-function/f (fn [db]
                      (assoc-in db [:mf-editor/editors :example-editor]
                                (create-example-editor)))})
