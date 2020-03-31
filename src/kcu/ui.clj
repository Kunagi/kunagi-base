(ns kcu.ui)


(defmacro def-component
  [& args]
  (let [[component-f options] args
        id (keyword (str (ns-name *ns*)) (str (name component-f)))
        model-type (keyword (name component-f))]
    `(reg-component ~id ~component-f ~model-type ~options)))


(macroexpand-1 '(def-component Patient))
