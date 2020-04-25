(ns lganguly.scrape
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]))

(defn url->title
  [url]
  (or (first
       (keep (fn [{:keys [attrs]}]
               (when (or (#{"og:title" "twitter:title"} (:property attrs))
                         (#{"og:title" "twitter:title"} (:name attrs)))
                 (:content attrs)))
             (-> url
                 http/get
                 :body
                 html/html-snippet
                 (html/select [:meta]))))
      (-> url
          http/get
          :body
          html/html-snippet
          (html/select [:title])
          first
          :content
          first)))


(defn url->title-html
  " (url->title-html \"https://blog.ndk.io/clojure-compilation.html\")
   ;; => How to get HTTPS working on your local development environment in 5 minutes <a href=\"https://www.freecodecamp.org/news/how-to-get-https-working-on-your-local-development-environment-in-5-minutes-7af615770eec/amp/\" target=\"_blank\">https://www.freecodecamp.org/news/how-to-get-https-working-on-your-local-development-environment-in-5-minutes-7af615770eec/amp/</a>"
  [url]
  (try
    (let [title (url->title url)]
      (format "%s <a href=\"%s\" target=\"_blank\">%s</a>"
              title
              url
              url))
    (catch Exception e (log/error "Error in" url))))


(defn url->title-html-main
  [& urls]
  (println)
  (doseq [url urls]
    (println (url->title-html url))
    (println)))
