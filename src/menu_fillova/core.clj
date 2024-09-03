(ns menu-fillova.core
  (:gen-class)
  (:require
   [menu-fillova.server :refer [start-server]]))

(def port 8080)

;; main
(defn -main [& _args]
  (start-server port)
  (deref (promise)))

