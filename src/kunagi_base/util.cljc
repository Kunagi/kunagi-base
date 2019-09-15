(ns kunagi-base.util
  (:refer-clojure :exclude [assert]))

(defmacro assert
  "Check if the given form is truthy, otherwise throw an exception with
  the given message and some additional context. Alternative to
  `assert` with Exceptions instead of Errors."
  [form otherwise-msg & values]
  `(or ~form
       (throw (ex-info ~otherwise-msg
                       (hash-map :form '~form
                                 ~@(mapcat (fn [v] [`'~v v]) values))))))
