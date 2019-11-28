(ns kunagi.datumoj.schema.entity
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is]]
   [clojure.string :as string]

   [kunagi.datumoj.schema.entity.attr :as attr]))


(s/def ::ident simple-keyword?)
(s/def ::entity (s/keys :req-un [::ident]))


(defn complete [entity schema]
  (when-not (s/valid? ::entity entity)
    (throw (ex-info "Invalid entity"
                    {:spec (s/explain-str ::entity entity)
                     :entity entity
                     :schema schema})))
  (cond-> entity

    (nil? (-> entity :texts :label-1))
    (assoc-in [:texts :label-1]
              (-> entity :ident name string/capitalize))

    (nil? (-> entity :texts :label-n))
    (assoc-in [:texts :label-n]
              (or (when-let [label (-> entity :texts :label-1)]
                    (-> label string/capitalize (str "s")))
                  (-> entity :ident name string/capitalize (str "s"))))

    true
    (update :attrs (partial map #(attr/complete % entity schema)))))


(deftest complete-test
  (let [schema {:ident :test}

        e (complete {:ident :show} schema)
        _ (is (= "Show" (-> e :texts :label-1)))
        _ (is (= "Shows" (-> e :texts :label-n)))

        e (complete {:ident :show :texts {:label-1 "Presentation"}} schema)
        _ (is (= "Presentation" (-> e :texts :label-1)))
        _ (is (= "Presentations" (-> e :texts :label-n)))]))
