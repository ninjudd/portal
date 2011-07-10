(ns portal.core
  (:use gloss.core lamina.core
        [clojure.string :only [join split]]))

(def message
  (compile-frame
   (string :utf-8 :length (prefix (string-integer :ascii :delimiters [":"]) inc dec))
   #(str (join " " %) ",")
   #(split (apply str (butlast %)) #" " 3)))
