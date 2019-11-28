(ns kunagi.datumoj.schema.entity.attr
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [deftest is]]
   [clojure.string :as string]))


(s/def ::ident simple-keyword?)
(s/def ::type simple-keyword?)
(s/def ::unique? boolean?)
(s/def ::attr (s/keys :req-un [::ident]
                      :opt-un [::type ::unique?]))


(defn type-ref-1? [attr])


(defn type-ref-n? [attr]
  (= :ref-n (-> attr :type)))


(defn type-ref? [attr]
  (or (type-ref-1? attr)
      (type-ref-n? attr)))


(defn complete [attr entity schema]
  (when-not (s/valid? ::attr attr)
    (throw (ex-info "Invalid attr"
                    {:spec   (s/explain-str ::attr attr)
                     :attr attr
                     :entity entity
                     :schema schema})))
  (cond-> attr

    (= :ref-1 (-> attr :type))
    (assoc :ref-1? true
           :ref? true)

    (= :ref-n (-> attr :type))
    (assoc :ref-n? true
           :ref? true)

    (nil? (-> attr :texts :label-1))
    (assoc-in [:texts :label-1]
              (-> attr :ident name string/capitalize))

    (nil? (-> attr :texts :label-n))
    (assoc-in [:texts :label-n]
              (or (when-let [label (-> attr :texts :label-1)]
                    (-> label string/capitalize (str "s")))
                  (-> attr :ident name string/capitalize (str "s"))))))


(deftest complete-test
  (let [schema {:ident :test}
        e {:ident :show}

        a (complete {:ident :title} e schema)
        _ (is (= "Title" (-> a :texts :label-1)))
        _ (is (= "Titles" (-> a :texts :label-n)))

        a (complete {:ident :title :texts {:label-1 "Name"}} e schema)
        _ (is (= "Name" (-> a :texts :label-1)))
        _ (is (= "Names" (-> a :texts :label-n)))]))
