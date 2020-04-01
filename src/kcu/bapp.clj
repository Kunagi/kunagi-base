(ns kcu.bapp)



(defmacro def-lense
  [sym lense]
  (let [k (keyword (str sym))
        id (keyword (str (ns-name *ns*)) (str sym))]
    `(def ~sym (reg-lense (merge {:id ~id
                                  :key ~k}
                                 ~lense)))))


(defmacro def-component
  [& args]
  (let [[component-f options] args
        id (keyword (str (ns-name *ns*)) (str (name component-f)))
        model-type (keyword (name component-f))]
    `(reg-component ~id ~component-f ~model-type ~options)))


#_(macroexpand-1 '(def-component Patient))
