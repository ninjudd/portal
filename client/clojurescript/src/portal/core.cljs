(ns portal.core
  (:require [cljs.nodejs :as node]
            [clojure.string :as string]))

(def net (node/require "net"))

(def contexts (atom {}))

(defn context
  "Look up a context. If no context exists, create one."
  [id]
  (let [id (str id)]
    (or (@contexts id)
        (get
         (swap! contexts assoc id
                {:results {}
                 :eval 0
                 :result 0
                 :callbacks {}
                 :stdout []
                 :stderr []})
         id))))

(defn update-context
  "Update a context."
  [id key f & args]
  (let [id (str id)]
    (context id)
    (get-in
     (swap! contexts update-in [id key] (fn [old] (apply f old args)))
     [id key])))

(defn netstring
  "Create a netstring."
  [message] (str (count message) ":" message ","))

(defn parse-message
  "Parse a Portal message."
  [message]
  (let [message (second (split (string/join (butlast (str message))) #":" 2))
        [id type content] (split message #" " 3)]
    (prn "message:" message)
    {:type (keyword type)
     :id id
     :content (string/join " " content)}))

(defn send-message
  "Send a portal message."
  [sock id type content]
  (.write sock (netstring (string/join " " [id type content]))))

(defn eval
  "Evaluate code."
  ([sock form callback] (eval sock form (rand-int 9999999999999999) callback))
  ([sock form id callback]
     (send-message sock id "eval" form)
     (update-context id :callbacks assoc
                     (update-context id :eval inc)
                     callback)))

(defn tail
  "Tail stdout or stderr from a context."
  [type id] (-> type context peek println))

(defn on-data [data]
  (let [{:keys [id type content]} (parse-message data)]
    (cond (#{:stdout :stderr} type)
          (update-context id type conj content)
          (#{:result :error :read-error} type)
          (let [count (update-context id :result inc)]
            #_(prn @contexts)
            ((get-in (context id) [:callbacks count])
             (let [vals (butlast (.split content "\n"))]
               (if (= type :result)
                 vals
                 (conj (butlast vals) {type (last vals)}))))))
    #_(prn data)))

(defn connect
  ([port] (connect port "localhost"))
  ([port host]
     (doto (.createConnection net port host)
       (.on "data" on-data))))

(defn test-fn [data]
  (prn "result" data))

(defn -main [& args]
  (let [sock (connect 1337 "localhost")]
    (eval sock "(+ 1 2 3 4 5)" test-fn)
    (eval sock "(slurp \"http://lazybot.org\")" test-fn)))

(set! *main-cli-fn* -main)