(ns bellatrix.utils
  (:require [clj-time.core :as ctc]
            [clj-time.format :as ctf]))

(defmacro with-time-reporting
  [& body]
  `(let [start# (. System (nanoTime))
         ret# (do ~@body)]
     {:result ret#
      :time-taken (/ (double (- (. System (nanoTime)) start#)) 1000000.0)}))

(defn time-str
  []
  (ctf/unparse (:basic-date-time-no-ms ctf/formatters) (ctc/now)))
