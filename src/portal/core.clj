(ns portal.core
  (:use gloss.core lamina.core
        [clojure.string :only [join split]]))

(defn read-seq [string]
  (with-in-str string
    (loop [forms []]
      (let [form (read *in* false ::EOF)]
        (if (= ::EOF form)
          forms
          (recur (conj forms form)))))))

(def message
  (compile-frame
   (string :utf-8 :length (prefix (string-integer :ascii :delimiters [":"]) inc dec))
   #(str (join " " %) ",")
   #(split (apply str (butlast %)) #" " 3)))
