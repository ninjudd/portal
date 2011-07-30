(ns portal.core
  (:require [cljs.nodejs :as node]
            [clojure.string :as string]))

(def net (node/require "net"))

(def contexts (atom {}))

(defn context
  "Look up a context. If no context exists, create one."
  [id]
  (or (@contexts id)
      (get
       (swap! contexts assoc id
              {:results []
               :count 0
               :stdout []
               :stderr []})
       id)))

(defn update-context
  "Update a context."
  [id key f & args]
  (context id)
  (swap! contexts update-in [id key] (fn [old] (apply f old args))))

(defn netstring
  "Create a netstring."
  [message] (str (count message) ":" message ","))

(defn parse-message
  "Parse a Portal message."
  [message]
  (let [message (second (.split (apply str (butlast (str message)))":"))
        [id type content] (.split message " ")]
    {:type (keyword type)
     :id id
     :content content}))

(defn send-message
  "Send a portal message."
  [sock id type content]
  (.write sock (netstring (string/join " " [id type content]))))

(defn eval
  "Evaluate code."
  ([sock form] (eval sock form (rand-int 9999999999999999)))
  ([sock form id & [callback]]
     (send-message sock id "eval" form)))

(defn tail
  "Tail stdout or stderr from a context."
  [type id] (-> type context peek println))

(defn on-data [data]
  (let [{:keys [id type content]} (parse-message data)]
    (cond (#{:stdout :stderr} type)
          (update-context id type conj content)
          (#{:result :error :read-error} type)
          (update-context id :results conj [type content]))
    (prn data)))

(defn connect
  ([port] (connect port "localhost"))
  ([port host]
     (let [sock (.createConnection net port host)]
       (.on sock "data" on-data))))

(defn -main [& args]
  (let [sock (connect 1337 "localhost")]
    (eval sock "(range 1000)")
    (eval sock "(+ 5 5)")))

(set! *main-cli-fn* -main)