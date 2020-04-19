(ns kcu.utils
  #?(:cljs (:require-macros [kcu.utils]))
  (:refer-clojure :exclude [read-string random-uuid])
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.spec.alpha :as s]
   [#?(:cljs cljs.reader :clj clojure.edn) :refer [read-string]]))


;; FIXME x must be uniqe!!!!
(defmacro do-once [& body]
  `(defonce x# (do ~@body)))

#_(macroexpand-1 '(do-once :a :b))


;;; maps ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn deep-merge [& maps]
  (apply merge-with (fn [& args]
                      (if (every? map? args)
                        (apply deep-merge args)
                        (last args)))
         maps))


(defn assoc-missing
  [m & key-value-fs]
  (reduce (fn [m [k f]]
            (if (contains? m k)
              m
              (assoc m k (if (fn? f)
                           (f m)
                           f))))
          m key-value-fs))


(defn getm
  "get mandatory"
  ([m k]
   (when-not m
     (throw (ex-info (str "No map to get key `" k "` from.")
                     {:k k})))
   (if-let [v (get m k)]
     v
     (throw (ex-info (str "No value for key `" k
                          "`. Keys: `" (keys m) "`.")
                     {:m m
                      :k k}))))
  ([m k spec]
   (let [v (getm m k)]
     (if (s/valid? spec v)
       v
       (throw (ex-info (str "Invalid value for key `" k "`. "
                            (s/explain-str spec v))
                       {:m m
                        :k k
                        :v v
                        :spec spec}))))))


;;; uuid ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn random-uuid []
  #?(:cljs (cljs.core/random-uuid)
     :clj  (java.util.UUID/randomUUID)))


(defn random-uuid-string []
  (str #?(:cljs (random-uuid)
          :clj  (java.util.UUID/randomUUID))))


;;; time ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn current-time-millis []
  #?(:cljs (.getTime (js/Date.))
     :clj  (System/currentTimeMillis)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn as-optional-fn
  "Coerce `function-or-value` to function if not nil."
  [function-or-value]
  (when function-or-value
    (if (fn? function-or-value)
      function-or-value
      (constantly function-or-value))))


;;; assertions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn assert-spec
  ([spec value]
   (assert-spec spec value nil))
  ([spec value otherwise-msg]
   (if (s/valid? spec value)
     value
     (throw (ex-info (str (when otherwise-msg
                            (if (qualified-keyword? otherwise-msg)
                              (str "Assertion in "
                                   (pr-str otherwise-msg)
                                   " failed.\n")
                              (str otherwise-msg "\n")))
                          "Value does not conform to spec "
                          spec
                          ": "
                          (pr-str value))
                     {:spec spec
                      :value value
                      :spec-explain (s/explain-str spec value)})))))


(defn assert-entity!
  [entity req opt subject-description]
  (when-not entity
    (throw (ex-info (str "Asserting entity "
                         subject-description
                         " failed. "
                         "Entity is `nil`.")
                    {})))
  (when-not (map? entity)
    (throw (ex-info (str "Asserting entity "
                         subject-description
                         " failed. "
                         "Entity is not a map: `"
                         entity
                         "`.")
                    {})))
  (doseq [k (keys req)]
    (when-not (contains? entity k)
      (throw (ex-info (str "Asserting entity "
                           subject-description
                           " failed. "
                           "Missing mandatory attribute `"
                           k
                           "`.")
                      {:entity entity
                       :missing-key k
                       :req req
                       :opt opt}))))
  (doseq [[k spec] (into req opt)]
    (when-let [v (get entity k)]
      (when-not (s/valid? spec v)
        (throw (ex-info (str "Asserting entity "
                             subject-description
                             " failed. "
                             "Value for attribute `"
                             k
                             "` does not conform to spec `"
                             spec
                             "`: `" v "`.")
                        {:entity entity
                         :key k
                         :invalid-value v
                         :spec spec
                         :spec-explain (s/explain-str spec v)
                         :req req
                         :opt opt})))))
  entity)


(defmacro assert-entity
  [& [entity req opt]]
  ;; TODO get callers function name
  (let [subject (str entity " in `" *ns* "`")]
    `(assert-entity! ~entity ~req ~opt ~subject)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn decode-edn
  [s]
  (when s (read-string s)))


(defn encode-edn
  ([value]
   (pr-str value))
  ([value pretty?]
   (if pretty?
     (with-out-str (pprint value))
     (pr-str value))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn invoke-later!
  [offset-millis f]
  #?(:cljs (js/setTimeout f offset-millis)
     :clj (future
            (Thread/sleep offset-millis)
            (f))))


;;; text ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn humanize-label [x]
  (when x
    (cond
      (simple-keyword? x) (name x)
      (qualified-keyword? x) (str (namespace x) " " (name x))
      :else (str x))))


;;; storage ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defprotocol Storage
  (store-value [storage storage-key value])
  (load-value [storage storage-key]))


(defn storage-caching-with-atom [cache-atom target-storage]
  "Storage which caches all values in a single atom."
  (reify Storage
    (store-value [_this k v]
      (when target-storage
        (store-value target-storage k v))
      (swap! cache-atom assoc k v))
    (load-value [this k]
      (if-let [value-from-cache (get @cache-atom k)]
        value-from-cache
        (when-let [value (load-value target-storage k)]
          (swap! cache-atom assoc k value)
          value)))))
