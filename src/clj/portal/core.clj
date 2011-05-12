(ns portal.core
  (:use gloss.core lamina.core
        [clojure.string :only [join split]]))

(def netstring
  (finite-frame
   (prefix (string-integer :ascii :delimiters [":"]) inc dec)
   (string :utf-8 :delimiters [","])))

(defn read-seq [string]
  (with-in-str string
    (loop [forms []]
      (let [form (read *in* false ::EOF)]
        (if (= ::EOF form)
          forms
          (recur (conj forms form)))))))

(defn decode-message [string]
  (split (str string) #" " 3))

(defn encode-message [id type data]
  (str id " " type " "
       (case type
         "result"     (join (map prn-str data))
         "error"      (str (.getName (class data)) " " (.getMessage data))
         "read-error" (.getMessage data)
         data)))

(defn enqueue-message [ch id [type data]]
  (enqueue ch (encode-message id type data)))
