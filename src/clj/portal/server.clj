(ns portal.server
  (:use portal.core portal.io lamina.core aleph.tcp
        [clojure.stacktrace :only [root-cause]]
        [useful :only [update conj-set]])
  (:import (clojure.lang LispReader$ReaderException)))

(def contexts (atom {}))

(def ^{:dynamic true} *pipe* nil)

(defn- gen-context [context id]
  (or context
      (let [[reader writer] (pipe)
            channels        #(get (meta @contexts) id)]
        (clojure.main/with-bindings
          (binding [*pipe* writer
                    *in*   reader
                    *out*  (context-writer channels id "stdout")
                    *err*  (context-writer channels id "stderr")]
            (ns user)
            (agent (get-thread-bindings)))))))

(defn get-context [id]
  (or (get @contexts id)
      (get (swap! contexts update id gen-context id) id)))

(defmacro with-context
  "Execute the given forms in the context associated with id."
  [channel id & forms]
  `(do (swap! contexts vary-meta update ~id conj-set ~channel)
       (send (get-context ~id)
             (fn [bindings#]
               (try (push-thread-bindings bindings#)
                    ~@forms
                    (dissoc (get-thread-bindings) #'*agent*)
                    (finally (pop-thread-bindings)))))))

(defn clear-context [contexts channel id]
  (let [contexts (vary-meta contexts update id disj channel)]
    (if (empty? (get (meta contexts) id))
      (dissoc contexts id)
      contexts)))

(defn read-eval [data]
  (try (let [forms (read-seq data)]
         (try ["result" (doall (map eval forms))]
              (catch Exception e
                ["error" (root-cause e)])))
       (catch LispReader$ReaderException e
         ["read-error" (root-cause e)])))

(defn handler [channel client-info]
  (receive-all channel
    (fn [frame]
      (let [[id type data] (decode-message frame)]
        (case type
          "stdin" (binding [*out* (get @(get-context id) #'*pipe*)]
                    (print data)
                    (flush))
          "eval"  (with-context channel id
                    (enqueue-message channel id (read-eval data)))
          "fork"  (swap! contexts
                         #(assoc % data (get % id)))
          "clear" (swap! contexts clear-context channel id)
          (enqueue-message channel id ["invalid" type]))))))

(defn start [port]
  (start-tcp-server handler {:port port, :frame netstring}))
