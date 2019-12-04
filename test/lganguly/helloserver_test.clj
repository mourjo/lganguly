(ns lganguly.helloserver-test
  (:require [clj-http.client :as chc]
            [clj-http.cookies :as chcookie]
            [clj-http.core :as chcore]
            [clj-time.core :as ctc]
            [clojure.test :refer :all]
            [lganguly.helloserver :as sut]))

(defn once-fixture
  [tests]
  (try
    (sut/-main)
    (tests)
    (finally
      (sut/stop-server))))


(use-fixtures :once once-fixture)


(deftest wrap-session-timeout-test
  (let [counter (atom 0)
        composed-handler (sut/wrap-session-timeout
                          (fn [& _]
                            (swap! counter inc)
                            {:body "Hello!"}))]

    (is (= {:body "Hello!"}
           (composed-handler {:session {:created-at (ctc/now)}}))
        "The middleware lets valid sessions that have not timed out pass through")

    (is (= 1 @counter)
        "The middleware lets the request pass through to the handler when session is active")

    (reset! counter 0)

    (is (= {:status 302 :headers {"Location" "/login"} :body "" :session nil}
           (composed-handler {:session {:created-at (ctc/epoch)}}))
        "The middleware forcefully logs the user out when the session has timedout")

    (is (zero? @counter)
        "The middleware does not pass the request to the handler when the session has timed out")))


(deftest invalid-login-test
  (let [response (chc/get "http://localhost:4004")]
    (is (= ["http://localhost:4004/hello"
            "http://localhost:4004/login"]
           (:trace-redirects response))
        "When not logged in, redirect to login page"))

  (let [response (chc/get "http://localhost:4004/stats")]
    (is (empty? (:trace-redirects response))
        "The stats page does not need login information"))

  (let [response (chc/post "http://localhost:4004/login"
                           {:form-params {"username" "lganguly"
                                          "password" "password"}})]
    (is (empty? (:trace-redirects response))
        "Invalid login credentials does not let the user through")))


(deftest valid-login-test
  (binding [chcore/*cookie-store* (chcookie/cookie-store)]
    (let [response (chc/post "http://localhost:4004/login"
                             {:form-params {"username" "lganguly"
                                            "password" "lganguly"}})]
      (is (= ["http://localhost:4004/hello"]
             (:trace-redirects response))
          "Valid login credentials takes the user to the hello page"))

    (let [response (chc/get "http://localhost:4004/login")]
      (is (= ["http://localhost:4004/hello"]
             (:trace-redirects response))
          "As long as the user is logged in, they are allowed to go to the hello page"))

    (let [cookie-attribs (-> chcore/*cookie-store*
                             chcookie/get-cookies
                             (get "awesome-cookie")
                             (select-keys [:discard :domain :path]))]
      (is (= {:discard true
              :domain "localhost"
              :path "/"}
             cookie-attribs)
          "Session cookie is set correctly"))))


(deftest session-timeout-test
  (binding [chcore/*cookie-store* (chcookie/cookie-store)]
    (chc/post "http://localhost:4004/login"
              {:form-params {"username" "lganguly"
                             "password" "lganguly"}})

    (is (empty? (:trace-redirects (chc/get "http://localhost:4004/hello")))
        "While the session is active, the user should be allowed to go through")

    ;; don't wait for 10s, mock the time calculation
    (let [now+20s (ctc/plus (ctc/now) (ctc/seconds 20))]
      (with-redefs [ctc/now (constantly now+20s)]
        (is (= ["http://localhost:4004/login"]
               (:trace-redirects (chc/get "http://localhost:4004/hello")))
            "After 10+s, the user should be auto logged out and redirected to login")))))


(deftest metrics-test
  (reset! sut/db {})
  (binding [chcore/*cookie-store* (chcookie/cookie-store)]
    (chc/post "http://localhost:4004/login"
              {:form-params {"username" "lganguly"
                             "password" "lganguly"}})

    (dotimes [_ 2] (chc/get "http://localhost:4004/hello")))

  ;; new session with new user
  (binding [chcore/*cookie-store* (chcookie/cookie-store)]
    (chc/post "http://localhost:4004/login"
              {:form-params {"username" "lalmohan_jatayu"
                             "password" "lalmohan_jatayu"}}))

  (is (= (:route-counters @sut/db)
         {"/hello/" 4
          "/login/" 2}))

  (is (= (set (:login-audits @sut/db))
         #{"lalmohan_jatayu" "lganguly"})))
