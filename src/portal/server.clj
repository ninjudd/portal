(ns portal.server
  (:use portal.core portal.io lamina.core aleph.tcp
        [clojure.stacktrace :only [root-cause]])
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
      (get (swap! contexts update-in [id] gen-context id) id)))

(defmacro with-context
  "Execute the given forms in the context associated with id."
  [channel id & forms]
  `(do (swap! contexts vary-meta update-in [~id] (fnil conj #{}) ~channel)
       (send (get-context ~id)
             (fn [bindings#]
               (try (push-thread-bindings bindings#)
                    ~@forms
                    (dissoc (get-thread-bindings) #'*agent*)
                    (finally (pop-thread-bindings)))))))

(defn close-context [contexts channel id]
  (let [contexts (vary-meta contexts update-in [id] disj channel)]
    (if (empty? (get (meta contexts) id))
      (dissoc contexts id)
      contexts)))

(defn read-eval-print []
  (try (let [form (read *in* false ::EOF)]
         (when (not= ::EOF form)
           (try (let [val (eval form)]
                  (set! *3 *2)
                  (set! *2 *1)
                  (set! *1 val)
                  (prn-str val))
                (catch Exception e
                  (set! *e e)
                  (let [e (root-cause e)]
                    {:error (str (.getName (class e)) " " (.getMessage e))})))))
    (catch LispReader$ReaderException e
      (set! *e e)
      {:read-error (.getMessage (root-cause e))})))

(defn read-eval-print-loop [string]
  (with-in-str string
    (let [[vals [{:keys [error read-error]}]]
          (split-with string? (repeatedly read-eval-print))
          result (apply str vals)]
      (cond error      ["error"      (str result error)]
            read-error ["read-error" (str result read-error)]
            :else      ["result"     result]))))

(defn handler [channel client-info]
  (receive-all channel
    (fn [[id type data]]
      (case type
        "stdin" (binding [*out* (get @(get-context id) #'*pipe*)]
                  (print data)
                  (flush))
        "eval"  (with-context channel id
                  (enqueue channel (apply vector id (read-eval-print-loop data))))
        "fork"  (swap! contexts
                       #(assoc % data (get % id)))
        "close" (swap! contexts close-context channel id)
        (enqueue channel [id "invalid" type])))))

(defn start [port]
  (start-tcp-server handler {:port port, :frame message}))
