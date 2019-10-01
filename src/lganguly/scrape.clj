(ns lganguly.scrape
  (:require [clj-http.client :as http]
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
  "(url->title-html \"https://blog.ndk.io/clojure-compilation.html\")
   ;; => Clojure Compilation: Parenthetical Prose to Bewildering Bytecode <a href="https://blog.ndk.io/clojure-compilation.html" target="_blank">https://blog.ndk.io/clojure-compilation.html</a>"
  [url]
  (let [title (url->title url)]
    (format "%s <a href=\"%s\" target=\"_blank\">%s</a>"
            title
            url
            url)))
