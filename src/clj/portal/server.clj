(ns portal.server
  (:use lamina.core gloss.core aleph.tcp
        [clojure.stacktrace :only [root-cause]]
        [clojure.string :only [join]])
  (:import (java.io PipedWriter PipedReader)
           (clojure.lang LineNumberingPushbackReader LispReader$ReaderException)))

(def netstring
  (finite-frame
   (prefix (string-integer :ascii :delimiters [":"]) inc dec)
   (string :utf-8 :delimiters [","])))

(def line
  (string :utf-8 :delimiters ["\r\n"]))

(defn decode-message [s]
  (with-in-str (str s)
    (let [id   (read)
          type (read)]
      (case type
        :stdin [id type (.substring (slurp *in*) 1)]
        :clear [id type nil]
        (if (contains? #{:eval :echo} type)
          (try [id type (read)]
               (catch LispReader$ReaderException e
                 [id :read-error (.getMessage (root-cause e))]))
          [id :invalid type])))))

(defn encode-message [id type form]
  (if (contains? #{:stdout :stderr} type)
    (join " " (concat (map pr-str [id type]) [form]))
    (join " " (map pr-str [id type form]))))

(defn eval-form [id form]
  (try (encode-message id :result (eval form))
       (catch Exception e
         (let [e (root-cause e)]
           (encode-message id :error [(.getName (class e)) (.getMessage e)])))))

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
      (let [[id type form] (decode-message frame)]
        (prn id type form)
        (case type
          :stdin (binding [*out* (get @(get-context id) #'*pipe*)]
                   (println form))
          :eval  (send (get-context id)
                       (fn [bindings]
                         (try (push-thread-bindings bindings)
                              (enqueue ch (eval-form id form))
                              (get-thread-bindings)
                              (finally (pop-thread-bindings)))))
          :clear (clear-context! id)
          (enqueue ch (encode-message id type form)))))))

(defn start [port]
  (start-tcp-server handler {:port port, :frame netstring}))
