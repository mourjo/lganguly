(defproject lganguly "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.0"]
                 [compojure "1.6.1"]
                 [clj-time "0.15.2"]
                 [enlive "1.1.6"]
                 [clj-http "3.10.0"]
                 [org.clojure/core.async "0.4.500"]
                 [org.clojure/tools.logging "0.5.0"]]
  :main ^:skip-aot lganguly.compojure
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
