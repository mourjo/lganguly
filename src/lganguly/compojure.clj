(ns lganguly.compojure
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]))


(defonce server nil)

(defn render-blog
  [& args]
  "<h1>BLOG!</h1>")


(defn simple-handler
  [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Hello World"})


(defroutes compojure-handler
  (GET "/" [] "<h1>Hello World</h1>")
  (GET "/blog" [] render-blog)
  (route/not-found "<h1>Page not found</h1>"))


(defn -main
  [& args]
  (alter-var-root #'server
                  (constantly (jetty/run-jetty compojure-handler
                                               {:port 4004 :join? false}))))


(defn stop-server
  []
  (.stop server))


(defn restart-server
  []
  (try (stop-server) (catch NullPointerException _))
  (-main))
