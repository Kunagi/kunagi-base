(ns kunagi-base.mf.editor
  (:require
   [kunagi-base.mf.model :as model]
   [kunagi-base.mf.extension :as extension]
   [kunagi-base.mf.view :as view]))



(defn new-editor []
  {:model (-> (model/new-model)
              (model/update-facts [{:hello :world}
                                   {:another :entity}]))
   :view nil
   :extensions []})


(defn add-extension [editor extension]
  (update editor :extensions conj extension))


(defn extension [editor extension-ident]
  (->> editor
       :extensions
       (filter #(= extension-ident (get % :ident)))
       first))


(defn current-view [editor]
  (get editor :view))


(defn activate-view [editor extension-ident view-ident]
  (let [extension (extension editor extension-ident)
        view (extension/view extension view-ident)]
    (assoc editor :view view)))


(defn node-tree [editor]
  (when-let [view (current-view editor)]
    (view/node-tree view editor nil)))
