(ns portal.server
  (:use lamina.core gloss.core aleph.tcp
        [clojure.stacktrace :only [root-cause]]
        [clojure.string :only [join split]])
  (:import (java.io PipedWriter PipedReader)
           (clojure.lang LineNumberingPushbackReader LispReader$ReaderException)))

(def netstring
  (finite-frame
   (prefix (string-integer :ascii :delimiters [":"]) inc dec)
   (string :utf-8 :delimiters [","])))

(defn- read-seq [string]
  (with-in-str string
    (loop [forms []]
      (let [form (read *in* false ::EOF)]
        (if (= ::EOF form)
          forms
          (recur (conj forms form)))))))

(defn decode-message [s]
  (let [[id type content] (split (str s) #"\s+" 3)]
    (cond (#{"stdin" "clear"} type) [id type content]
          (#{"eval"  "echo"}  type) (try [id type (read-seq content)]
                                         (catch LispReader$ReaderException e
                                           [id "read-error" (.getMessage (root-cause e))]))
          :else [id "invalid" type])))

(defn encode-message [id type content]
  (str id " " type " " (if (contains? #{"result" "echo"} type)
                         (join (map prn-str content))
                         content)))

(defn eval-forms [id forms]
  (try (encode-message id "result" (map eval forms))
       (catch Exception e
         (let [e (root-cause e)]
           (encode-message id "error" (str (.getName (class e)) ": " (.getMessage e)))))))

(def *pipe* nil)

(def contexts (atom {}))

(defn- gen-context [context]
  (or context
      (clojure.main/with-bindings
        (binding [*pipe* (PipedWriter.)]
          (binding [*in* (LineNumberingPushbackReader. (PipedReader. *pipe*))]
            (in-ns (gensym 'portal))
            (refer 'clojure.core)
            (agent (get-thread-bindings)))))))

(defn get-context [id]
  (or (get @contexts id)
      (get (swap! contexts update-in [id] gen-context) id)))

(defn clear-context! [id]
  (swap! contexts dissoc id))

(defn handler [ch client-info]
  (receive-all ch
    (fn [frame]
      (let [[id type content] (decode-message frame)]
        (case type
          "stdin" (binding [*out* (get @(get-context id) #'*pipe*)]
                    (print content)
                    (flush))
          "eval"  (send (get-context id)
                        (fn [bindings]
                          (try (push-thread-bindings bindings)
                               (enqueue ch (eval-forms id content))
                               (get-thread-bindings)
                               (finally (pop-thread-bindings)))))
          "clear" (clear-context! id)
          (enqueue ch (encode-message id type content)))))))

(defn start [port]
  (start-tcp-server handler {:port port, :frame netstring}))
