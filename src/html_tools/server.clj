(ns html-tools.server
  (:require
   [clojure.java.io :as io]

   [ring.middleware.file :as ring-file]
   [ring.middleware.reload :as ring-reload]
   [ring.adapter.jetty :as jetty]

   [html-tools.api :as html]))


(def port 9500)


(defn serve-file [website-config req resource]
  (let [file (io/as-file (str "resources/" resource))]
    (if (.exists file)
      {:status 200
       :content "exists"}
      {:status 404})))


(defn serve-request [website-config req]
  (let [uri (:uri req)
        uri (if (= "/" uri) "/index.html" uri)
        uri (.substring uri 1) ; cause uri always starts with '/'
        page-config (get-in website-config [:pages uri])]
    (if page-config
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (html/page-html req (page-config req))}
      {:status 404})))


(defn run-http-server
  [website-config]
  (println "\nStarting JETTY:      --> " (str "http://localhost:" port "/") "\n")
  (-> (fn [req] (serve-request website-config req))
      (ring-file/wrap-file "resources")
      (ring-reload/wrap-reload)
      (jetty/run-jetty {:port port})))


(defn demo-index-page [req]
  {:modules [:bootstrap-cdn] :content "hello world"})

(def demo-website-config
  {:pages {"index.html" demo-index-page}})

(defn -main []
  (run-http-server demo-website-config))
