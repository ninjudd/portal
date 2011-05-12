(ns portal.server
  (:use portal.core lamina.core aleph.tcp
        [clojure.stacktrace :only [root-cause]])
  (:import (java.io PipedWriter PipedReader)
           (clojure.lang LineNumberingPushbackReader LispReader$ReaderException)))

(def contexts (atom {}))

(def ^{:dynamic true} *pipe* nil)

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

(defmacro with-context
  "Execute the given forms in the context associated with id."
  [id & forms]
  `(send (get-context ~id)
         (fn [bindings#]
           (try (push-thread-bindings bindings#)
                ~@forms
                (get-thread-bindings)
                (finally (pop-thread-bindings))))))

(defn clear-context! [id]
  (swap! contexts dissoc id))

(defn read-eval [data]
  (try (let [forms (read-seq data)]
         (try ["result" (doall (map eval forms))]
              (catch Exception e
                ["error" (root-cause e)])))
       (catch LispReader$ReaderException e
         ["read-error" (root-cause e)])))

(defn handler [ch client-info]
  (receive-all ch
    (fn [frame]
      (let [[id type data] (decode-message frame)]
        (case type
          "stdin" (binding [*out* (get @(get-context id) #'*pipe*)]
                    (print data)
                    (flush))
          "eval"  (with-context id
                    (enqueue-message ch id (read-eval data)))
          "clear" (clear-context! id)
          (enqueue-message ch id ["invalid" type]))))))

(defn start [port]
  (start-tcp-server handler {:port port, :frame netstring}))
