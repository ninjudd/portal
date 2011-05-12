(ns portal.client
  (:use portal.core lamina.core aleph.tcp))

(defn connect [port & [host]]
  (tcp-client {:host (or host "localhost") :port port, :frame netstring}))
