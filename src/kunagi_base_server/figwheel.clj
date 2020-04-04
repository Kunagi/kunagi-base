(ns kunagi-base-server.figwheel)


(defonce !started? (atom false))


(defn ring-handler [start-fn]
  (fn [request]
    (let [href (str "http://localhost:" 3000)]
      (if (and (= :get (:request-method request))
               (= "/"  (:uri request)))
        (do
          (when-not @!started?
            (reset! !started? true)
            (start-fn))
          {:status 302
           :headers {"Location" href}})
        {:status 404
         :headers {"Content-Type" "text/plain"}
         :body (str "404 - Page not found\n"
                    "\n"
                    "This is the ring handler for figwheel.\n"
                    "Goto " href)}))))
