(ns kunagi-base-browserapp.modules.desktop.api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [accountant.core :as accountant]
   [cemerick.url :refer (url url-encode)]

   [kcu.bapp :as bapp]
   [kunagi-base.utils :as utils]
   [kunagi-base.appmodel :as am]
   [kunagi-base-browserapp.modules.tracking.api :as tracking]
   [kunagi-base-browserapp.utils :refer [parse-location-params
                                         scroll-to-top!]]))


(s/def :desktop/page-ident simple-keyword?)
(s/def :desktop/page-args map?)


(defn- parse-location []
  (let [pathname (.. js/document -location -pathname)
        page (.substring pathname 4)
        page (if (empty? page) "index" page)
        page-ident (keyword page)
        page-args (parse-location-params)]
    [page-ident page-args]))


(defn page [page-ident]
  (when page-ident
    (am/entity! [:page/ident page-ident])))


(def navigate! bapp/navigate!)


(defn- activate-page
  [db page-ident page-args]
  (utils/assert-spec :desktop/page-ident page-ident ::activate-page.page-ident)
  (utils/assert-spec :desktop/page-args page-args ::activate-page.page-args)
  (let [current-page-ident (get db :desktop/current-page-ident)
        current-page-args (get-in db [:desktop/pages-args current-page-ident])]
    (if (and (= page-ident current-page-ident)
             (= page-args current-page-args))
      db
      (do
        (tap> [:dbg ::activate-page page-ident page-args])
        (tracking/track-screen-view! page-ident (if (empty? page-args)
                                                  nil
                                                  {"page_args" page-args}))
        (let [current-page (am/entity! [:page/ident current-page-ident])
              store-scroll-position? (-> current-page :page/store-scroll-position?)
              current-scroll-position (-> js/document .-documentElement .-scrollTop)
              page (am/entity! [:page/ident page-ident])
              restore-scroll-position? (-> page :page/store-scroll-position?)
              new-scroll-position (get-in db [:desktop/pages-scroll-positions page-ident page-args])
              on-activate-f (or (-> page :page/on-activate-f)
                                (fn [db page-args] db))]
          (scroll-to-top!)
          (when restore-scroll-position?
            (js/setTimeout
             #(js/window.scrollTo 0 (or new-scroll-position 0))
             50)
            (js/setTimeout
             #(js/window.scrollTo 0 (or new-scroll-position 0))
             250)
            (js/setTimeout
             #(js/window.scrollTo 0 (or new-scroll-position 0))
             500))
          (when-not (= [page-ident page-args] (parse-location))
            (navigate! page-ident page-args))
          (-> db
              (assoc :desktop/current-page-ident page-ident)
              (assoc-in [:desktop/pages-args page-ident] page-args)
              (assoc-in [:desktop/pages-scroll-positions current-page-ident current-page-args]
                        (when store-scroll-position? current-scroll-position))
              (on-activate-f page-args)))))))



(defn install-error-handler []
  (set! (.-onerror js/window)
        (fn [msg url line col error]
          (let [error-info {:msg msg
                            :url url
                            :line line
                            :col col
                            :error error}]
            (tap> [:err ::error error-info])
            (rf/dispatch [:desktop/error "JavaScript Error" error-info]))
          true)))




(rf/reg-sub
 :desktop/errors
 (fn [db]
   (get db :desktop/errors)))


(rf/reg-event-db
 :desktop/error
 (fn [db [_ msg data]]
   (update db :desktop/errors assoc (random-uuid) [msg data])))


(rf/reg-event-db
 :desktop/dismiss-error
 (fn [db [_ id]]
   (update db :desktop/errors dissoc id)))



(rf/reg-sub
 :desktop/current-page-ident
 (fn [db]
   (get db :desktop/current-page-ident)))


(rf/reg-sub
 :desktop/pages-args
 (fn [db]
   (get db :desktop/pages-args)))


(rf/reg-sub
 :desktop/current-page-args
 (fn [_]
   [(rf/subscribe [:desktop/current-page-ident])
    (rf/subscribe [:desktop/pages-args])])
 (fn [[page-ident pages-args]]
   (get pages-args page-ident)))


(rf/reg-sub
 :desktop/page-args
 (fn [_]
   [(rf/subscribe [:desktop/pages-args])
    (rf/subscribe [:desktop/current-page-ident])])
 (fn [[pages-args current-page-ident] [_ page-ident]]
   (get pages-args (or page-ident current-page-ident))))


(rf/reg-sub
 :desktop/current-page-workarea
 (fn [_]
   (rf/subscribe [:desktop/current-page-ident]))
 (fn [page-ident _]
   (if-let [page (page page-ident)]
     (-> page :page/workarea)
     [:div])))


(rf/reg-sub
 :desktop/current-page-toolbar
 (fn [_]
   (rf/subscribe [:desktop/current-page-ident]))
 (fn [page-ident _]
   (if-let [page (page page-ident)]
     (-> page :page/toolbar))))


(rf/reg-sub
 :desktop/current-page-title-text
 (fn [_]
   (rf/subscribe [:desktop/current-page-ident]))
 (fn [page-ident _]
   (if-let [page (page page-ident)]
     (-> page :page/title-text))))


(rf/reg-sub
 :desktop/current-page-back-button
 (fn [_]
   (rf/subscribe [:desktop/current-page-ident]))
 (fn [page-ident _]
   (if-let [page (page page-ident)]
     (-> page :page/back-button))))


(rf/reg-event-db
 :desktop/activate-page
 (fn [db [_ page-ident page-args]]
   (activate-page db page-ident page-args)))



(defn- accountant-nav-handler [path]
  (let [[page-ident page-args] (parse-location)]
    (rf/dispatch [:desktop/activate-page page-ident page-args])))


(defn install-accountant! []
  (accountant/configure-navigation!
   {:nav-handler accountant-nav-handler
    :path-exists? (fn [path] (str/starts-with? path "/ui/"))
    :reload-same-path? false})
  (accountant/dispatch-current!))

