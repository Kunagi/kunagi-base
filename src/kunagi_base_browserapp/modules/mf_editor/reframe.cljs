(ns kunagi-base-browserapp.modules.mf-editor.reframe
  (:require
   [re-frame.core :as rf]))


(rf/reg-sub
 :mf-editor/editor
 (fn [db [_ editor-id]]
   (get-in db [:mf-editor/editors editor-id])))


(rf/reg-sub
 :mf-editor/actions
 (fn [_]))
