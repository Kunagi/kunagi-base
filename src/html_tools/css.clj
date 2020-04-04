(ns html-tools.css)


(defn- style-val
  [v]
  (cond
    (keyword? v) (name v)
    :else (str v)))


(defn style
  [css-prop-map]
  (reduce
   (fn [s [k v]]
     (str s (name k) ": " (style-val v) "; "))
   ""
   css-prop-map))


(defn css
  [css]
  (reduce
   (fn [s [k v]]
     (str s (style-val k) " {" (style v) "}\n"))
   "\n"
   css))
