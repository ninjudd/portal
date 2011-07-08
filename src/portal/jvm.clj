(ns portal.jvm
  (:require [portal.server :as server]))

(defn init []
  (let [port 9999]
    (server/start port)
    (spit (System/getProperty "portal.pidfile") (str port "\n") :append true)))
