(ns menu-fillova.server
  (:require
   [clojure.java.io :as io]
   [menu-fillova.calendar :as calendar]
   [menu-fillova.css :as css]
   [menu-fillova.meal-menu-mk2 :as meal-menu]
   [menu-fillova.weather :as weather]
   [menu-fillova.webrender :refer [render-hiccup-to-png!]]
   [org.httpkit.server :as http]))

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
      (when meal-menu-model
        (meal-menu/render meal-menu-model))]
     [:div#calendar
      (calendar/render calendar-model)]
     [:div#weather {:style {:padding-top "16px"}}
      (weather/render weather-model)]]]])


(defn handler [_req]
  {:status 200
   :body (let [png-filename "/tmp/fillova.png"
               meal-menu-model (meal-menu/make-model!)
               calendar-model (calendar/make-model!)
               weather-model (weather/make-model!)
               page (render-page meal-menu-model
                                 calendar-model
                                 weather-model)]
           (render-hiccup-to-png! page
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
  (start-server 8080)
  (stop-server))
