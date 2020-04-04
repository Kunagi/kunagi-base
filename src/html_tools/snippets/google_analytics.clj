(ns html-tools.snippets.google-analytics)

(defn html-head-components [tracking-id]
  (when tracking-id
    ["\n"
     [:script
      {:async true
       :src (str "https://www.googletagmanager.com/gtag/js?id="
                 tracking-id)}]
     [:script (str "
window.dataLayer = window.dataLayer || [];
function gtag(){dataLayer.push(arguments);}
gtag('js', new Date());
gtag('config', '" tracking-id "');
")]
     "\n"]))
