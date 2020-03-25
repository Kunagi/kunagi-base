(ns kcu.init)


(defonce app-db-type (atom :agent))


(defonce dispatch
  (atom (fn [event _context]
          (throw (ex-info "Atom `dispatch` not initialized."
                          {:event event
                           :atom dispatch})))))
