(ns kcu.devcards
  #?(:cljs (:require-macros [kcu.devcards])))


(defonce REGISTRY (atom {}))


(defn devcards []
  (-> @REGISTRY vals))


(defn register [devcard]
  (let [id (get devcard :id)
        devcard (assoc devcard :ns   (if (qualified-keyword? id)
                                        (keyword (namespace id))
                                        :???)
                               :name (if (qualified-keyword? id)
                                       (keyword (name id))
                                       id))]
    (swap! REGISTRY assoc id devcard)))


(defmacro devcard [id component]
  (let [title (str (first component))
        code component]
    (prn "\n\n" &env "\n\n")
    (when-not (= :release (:shadow.build/mode &env))
      `(register
        {:id ~id
         :title ~title
         :code '~code
         :component ~component}))))
