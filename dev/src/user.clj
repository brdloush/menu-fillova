(ns user
  (:require
   [menu-fillova.calendar :as calendar]
   [menu-fillova.meal-menu-mk2 :as meal-menu]
   [menu-fillova.server :refer [render-page start-server stop-server]]
   [menu-fillova.weather :as weather]
   [menu-fillova.webrender :refer [render-hiccup-to-png!]]))

(println "starting user ns")

;; dev
(def memoized-meal-menu-make-model (memoize meal-menu/make-model!))
(def memoized-calendar-make-model (memoize calendar/make-model!))
(def memoized-weather-make-model (memoize weather/make-model!))

(defn time! []
  (str (java.time.LocalDateTime/now)))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn go []
  (let [png-filename "/tmp/fillova.png"
        meal-menu-model (memoized-meal-menu-make-model)
        calendar-model (memoized-calendar-make-model)
        weather-model (memoized-weather-make-model)
        page (render-page meal-menu-model
                          calendar-model
                          weather-model)]
    (println (time!) "rendering page to" png-filename)
    (let [took (with-out-str
                 (time
                  (render-hiccup-to-png! page
                                       png-filename)))]
      (println "finished in:" took))
    nil))

(comment
  (start-server 8080)
  (stop-server)
  (go))
