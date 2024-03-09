(ns menu-fillova.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [hiccup2.core :as h]
            [menu-fillova.calendar :as calendar]
            [menu-fillova.css :as css]
            [menu-fillova.meal-menu :as meal-menu]
            [menu-fillova.weather :as weather]
            [menu-fillova.webrender :as wr]
            [org.httpkit.server :as http]))

(def port 8080)

(defn render-page [meal-menu-model
                   calendar-model
                   weather-model]
  [:html
   [:head
    [:style css/reset]]
   [:div
    [:div {:style {:font-size "20pt;"
                   :font-family "DejaVu Serif"}} 
     [:div#meal-menu {:style {:padding "24pt"
                              :height "530px"
                              :overflow "hidden"}}
      (meal-menu/render meal-menu-model)] 
     [:div#calendar
      (calendar/render calendar-model)] 
     [:div#weather
      (weather/render weather-model)]]]])

(defn render-page-to-png! [page-hiccup png-filename]
  (let [width 600
        height 800
        html (str (h/html page-hiccup))]
    (wr/render-html-to-png! html png-filename width height)))

;; server
(defn handler [_req]
  {:status 200
   :body (let [png-filename "/tmp/fillova.png" 
               meal-menu-model (meal-menu/make-model!)
               calendar-model (calendar/make-model!)
               weather-model (weather/make-model!)
               page (render-page meal-menu-model
                                 calendar-model
                                 weather-model)]
           (render-page-to-png! page
                                png-filename)
           (io/file png-filename))})

(defonce server (atom nil))

(defn start-server [port]
  (if-not @server
    (do
      (println "Starting http server on port" port)
      (reset! server
              (http/run-server
               #'handler
               {:port port}))
      (println "Server started on port" port)
      :started)
    :already-running))

(defn stop-server []
  (if-let [stop-fn @server]
    (do (println "Stopping server")
        (stop-fn)
        (reset! server nil)
        :stopped)
    :not-running))

(comment
  (start-server port)
  (stop-server))

;; main
(defn -main [& _args]
  (start-server port)
  (deref (promise)))

;; dev
(def memoized-meal-menu-make-model (memoize meal-menu/make-model!))
(def memoized-calendar-make-model (memoize calendar/make-model!))
(def memoized-weather-make-model (memoize weather/make-model!))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn go []
  (let [png-filename "/tmp/fillova.png" 
        meal-menu-model (memoized-meal-menu-make-model)
        calendar-model (memoized-calendar-make-model)
        weather-model (memoized-weather-make-model)
        page (render-page meal-menu-model
                          calendar-model
                          weather-model)]
    (render-page-to-png! page
                         png-filename)
    nil))


