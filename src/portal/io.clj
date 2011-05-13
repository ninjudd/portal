(ns portal.io
  (:use portal.core lamina.core
        [clojure.java.io :only [writer]]
        [clojure.string :only [join]])
  (:import (java.io Writer PrintWriter PipedWriter PipedReader)
           (clojure.lang LineNumberingPushbackReader)))

(defn pipe []
  (let [writer (PipedWriter.)
        reader (LineNumberingPushbackReader. (PipedReader. writer))]
    [reader writer]))

(defn context-writer [channels id type]
  (let [data (channel)]
    (writer
     (proxy [Writer] []
       (write
         ([string]
            (enqueue data (cond (string?  string) string
                                (integer? string) (char string)
                                :else             (join string))))
         ([string off len]
            (enqueue data (if (string? string)
                            (.substring string off (+ off len))
                            (join (->> string (drop off) (take len)))))))
       (flush []
         (let [string (join (channel-seq data))]
           (doseq [ch (channels)]
             (enqueue ch [id type string]))))))))
