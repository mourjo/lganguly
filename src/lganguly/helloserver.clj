(ns lganguly.helloserver
  (:require [clj-time.core :as ctc]
            [clojure.stacktrace :as cst]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.multipart-params :as ring-multipart]
            [ring.middleware.params :as ring-params]
            [ring.middleware.session :as ring-session]
            [ring.middleware.session.memory :as ring-session-memory]
            [ring.util.response :as ring-response]))

;;; ---------------------- In memory stores ----------------------

(defonce server nil)
(defonce session-store (ring-session-memory/memory-store))
(defonce db (atom {:route-counters (sorted-map)}))
(defonce max-session-timeout-sec 10)


;;; ---------------------- Utilities ----------------------

(defn remaining-session-time
  [request]
  (ctc/in-seconds (ctc/interval (get-in request [:session :created-at]) (ctc/now))))



;;; ---------------------- Middlewares ----------------------

(defn enforce-trailing-slash
  "Adapted from https://gist.github.com/dannypurcell/8215411"
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (->> (if (.endsWith uri "/") uri (str uri "/"))
           (assoc request :uri)
           handler))))


(defn wrap-session-timeout
  [handler]
  (fn [request]
    (if (seq (:session request))
      (if (< (remaining-session-time request) max-session-timeout-sec)
        (handler request)
        (assoc (ring-response/redirect "/login") :session nil))
      (handler request))))


(defn wrap-exceptions
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (with-out-str (cst/print-stack-trace t))))))


(defn wrap-request-counts
  [handler]
  (fn [request]
    (swap! db update-in [:route-counters (:uri request)] (fnil inc 0))
    (handler request)))


;;; ---------------------- handlers ----------------------

(defn render-login
  [request]
  (if (seq (:session request))
    (ring-response/redirect "/hello")
    "<form action=\"/login/\" method=\"POST\">
        Username :<br> <input type=\"text\" name=\"username\"> <br>
        Password :<br> <input type=\"password\" name=\"password\"> <br><br>
                       <input type=\"submit\" value=\"Submit\">
     </form>"))


(defn accept-login
  [{:keys [form-params] :as request}]
  (if (= (form-params "username")
         (form-params "password"))
    (do (swap! db update :login-audits conj (form-params "username"))
        (assoc (ring-response/redirect-after-post "/hello")
               :session {:username (form-params "username")
                         :created-at (ctc/now)}))
    (ring-response/redirect "/login")))


(defn say-hello
  [request]
  (if (seq (:session request))
    (format "Hello, %s!<br><br>You have %d seconds remaining."
            (get-in request [:session :username])
            (- max-session-timeout-sec (remaining-session-time request)))
    (ring-response/redirect "/login")))


(defn server-stats
  [request]
  (str "<table>"
       (reduce-kv (fn [acc r c]
                    (str acc
                         "<tr>"
                         "<td>" r "</td>"
                         "<td>" c "</td>"
                         "</tr>"))
                  "<tr>
                     <td><b>Route</b></td>
                     <td><b>Count</b></td>
                  </tr>"
                  (:route-counters @db))
       "</table>"))


(defn logout
  [request]
  (assoc (ring-response/redirect "/login")
         :session nil))


(defroutes compojure-handler
  (GET "/" [] (ring-response/redirect "/hello"))
  (GET "/login/" [] render-login)
  (POST "/login/" [] accept-login)
  (ANY "/logout/" [] logout)
  (GET "/hello/" [] say-hello)
  (GET "/stats/" [] server-stats)
  (ANY "*" [] (ring-response/redirect "/login")))


;;; ---------------------- application ----------------------

(def app
  (-> compojure-handler
      wrap-request-counts
      wrap-session-timeout
      enforce-trailing-slash
      (ring-session/wrap-session {:store session-store
                                  :cookie-name "awesome-cookie"
                                  :cookie-attrs {:path "/"
                                                 :http-only true}})
      ring-multipart/wrap-multipart-params
      ring-params/wrap-params
      wrap-exceptions))


;;; ---------------------- bootstrapping ----------------------

(defn -main
  [& args]
  (let [sv (jetty/run-jetty app {:port 4004 :join? false})]
    (alter-var-root #'server (constantly sv))))


(defn stop-server
  []
  (.stop server))


(defn restart-server
  []
  (try (stop-server) (catch NullPointerException _))
  (-main))
