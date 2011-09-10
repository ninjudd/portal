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

(defn trade!
  "Like swap!, except it returns the old value of the atom."
  [atom f & args]
  (with-local-vars [prev nil]
    (apply swap! atom
           (fn [val & args]
             (var-set prev val)
             (apply f val args))
           args)
    (var-get prev)))

(defn context-writer [channels id type]
  (let [data (atom [])]
    (writer
     (proxy [Writer] []
       (write
         ([string]
            (swap! data conj (cond (string?  string) string
                                   (integer? string) (char string)
                                   :else             (join string))))
         ([string off len]
            (swap! data conj (if (string? string)
                               (.substring string off (+ off len))
                               (join (->> string (drop off) (take len)))))))
       (flush []
         (let [string (join (trade! data empty))]
           (doseq [ch (channels)]
             (enqueue ch [id type string]))))))))

