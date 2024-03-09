(ns menu-fillova.weather
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [hiccup2.core :as h]
            [menu-fillova.css :as css]
            [menu-fillova.nameday :refer [find-namedays]]
            [menu-fillova.time :refer [czech-day-of-week format-czech-date
                                       prague-time!]]
            [menu-fillova.weather :as weather]
            [menu-fillova.webrender :refer [render-html-to-png!]]))

(defn download-prediction []
  (json/parse-string
   (slurp
    "https://api.open-meteo.com/v1/forecast?latitude=50.088&longitude=14.4208&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m&timezone=Europe%2FBerlin&forecast_days=2")
   true))

(defn parse-prediction-response [preciption-response]
  (let [hourly (:hourly preciption-response)]
    (mapv (fn [time
               temperature_2m
               relative_humidity_2m
               precipitation_probability
               precipitation
               weather_code
               wind_speed_10m]
            {:time time
             :temperature_2m temperature_2m
             :relative_humidity_2m relative_humidity_2m
             :precipitation_probability precipitation_probability
             :precipitation precipitation
             :weather_code weather_code
             :wind_speed_10m wind_speed_10m})
          (-> hourly :time)
          (-> hourly :temperature_2m)
          (-> hourly :relative_humidity_2m)
          (-> hourly :precipitation_probability)
          (-> hourly :precipitation)
          (-> hourly :weather_code)
          (-> hourly :wind_speed_10m))))

(defn download-and-parse-predictions []
  (let [predictions-response (download-prediction)]
    (parse-prediction-response predictions-response)))

(def weather-codes-mapping
  {0 {:png "wi-day-sunny.png"
      :description "Jasno"}
   1 {:png "wi-day-cloudy.png"
      :description "Polojasno"}
   2  {:png "wi-cloud.png"
       :description "Oblačno"}
   3  {:png "wi-cloudy.png"
       :description "Zataženo"}
   45 {:png "wi-fog.png"
       :description "Mlha"}
   48 {:png "wi-fog.png"
       :description "Námraza"}
   51 {:png "wi-day-sprinkle.png"
       :description "Slabé mrholení"}
   53 {:png "wi-day-sprinkle.png"
       :description "Střední mrholení"}
   55 {:png "wi-day-sprinkle.png"
       :description "Husté mrholení"}
   56 {:png "wi-day-sleet.png"
       :description "Slabé mrz. mrholení."}
   57 {:png "wi-day-sleet.png"
       :description "Silné mrz. mrholení."}
   61 {:png "wi-day-rain.png"
       :description "Slabý déšť"}
   63 {:png "wi-day-rain.png"
       :description "Střední déšť"}
   65 {:png "wi-day-rain.png"
       :description "Prudký déšť"}
   66 {:png "wi-day-sleet.png"
       :description "Slabý mrz. déšť"}
   67 {:png "wi-day-sleet.png"
       :description "Prudký mrz. déšť"}
   71 {:png "wi-day-snow.png"
       :description "Slabé sněžení"}
   73 {:png "wi-day-snow.png"
       :description "Střední sněžení"}
   75 {:png "wi-day-snow.png"
       :description "Silné sněžení"}
   77 {:png "wi-day-hail.png"
       :description "Sněhové vločky"}
   80 {:png "wi-day-showers.png"
       :description "Slabé přeháňky"}
   81 {:png "wi-day-showers.png"
       :description "Střední přeháňky"}
   82 {:png "wi-day-showers.png"
       :description "Silné přeháňky"}
   85 {:png "wi-day-snow-wind.png"
       :description "Slabé sněhové přeháňky"}
   86 {:png "wi-day-snow-wind.png"
       :description "Silné sněhové přeháňky"}
   95 {:png "wi-day-thunderstorm.png"
       :description "Bouřka s hromobitím"}
   96 {:png "wi-day-thunderstorm.png"
       :description "Bouřka s krupobitím"}
   99 {:png "wi-day-thunderstorm.png"
       :description "Bouřka se silným krupobitím"}})

(defn weather-code->png-image [code]
  (or (some-> weather-codes-mapping (get code) :png)
      "wi-na.png"))

(defn get-weather-description [code]
  (or (some-> weather-codes-mapping (get code) :description)
      (str "Neznámý kód " code)))

(defn resources-dir-location []
  (or (System/getenv "RESOURCES_DIR")
      (str (fs/path (.toString (fs/cwd)) "resources"))))

(defn weather-icon-url [filename]
  (str "file://" (resources-dir-location) "/weather-icons/png/" filename))

(defn render-prediction [{:keys [time
                                 weather_code
                                 temperature_2m
                                 precipitation
                                 precipitation_probability
                                 wind_speed_10m] :as _prediction}]
  (let [weather-icon-png (weather-icon-url (weather-code->png-image weather_code))
        _raindrop-icon-png (weather-icon-url "wi-raindrops.png")
        weather-description (get-weather-description weather_code)]
    [:div {:style {:background-color "#FFF"
                   :float "left"
                   :width "200px"
                   #_#_:padding "12px"}}
     [:div {:style {:height "72px"}}
      [:center
       [:img {:src weather-icon-png
              :style {:background-color "#FFF"}
              :width "72px"}]]]
     [:div {:style {:font-size "18pt"
                    :padding "2pt 16pt"
                    :text-align "center"}}
      (second (re-find #"T(.+)$" time))]
     [:div {:style {:font-size "18pt"
                    :padding "8pt 16pt"
                    :text-align "center"}}
      weather-description]
     [:div {:style {:font-size "18pt"
                    :padding "4pt 16pt"
                    :text-align "center"}}
      (str (java.lang.Math/round temperature_2m) "˚C")]]))

(defn render-calendar-day-info []
  (let [today (prague-time!)
        czech-day-name (czech-day-of-week today)
        formatted-date (format-czech-date today)
        namedays (find-namedays today)
        namedays-fragment (if (not-empty namedays)
                            (str ", svátek: " (str/join "/" namedays))
                            "")]
    [:div czech-day-name " " formatted-date namedays-fragment]))

(defn render-predictions-row [parsed-predictions]
  (let [plus-days 0
        hours-offset (* plus-days 24)]
    [:div
     [:div {:style {:padding-left "16px"
                    :padding-right "16px"}}
      (render-calendar-day-info)]
     [:div {:style {:width "100%"}}
      (render-prediction (nth parsed-predictions (+ hours-offset 8)))
      (render-prediction (nth parsed-predictions (+ hours-offset 12)))
      (render-prediction (nth parsed-predictions (+ hours-offset 16)))]]))

(comment
  (do
    (defn render-page [parsed-predictions]
      (h/html
       [:html
        [:head
         [:style css/reset]]
        [:body
         (render-predictions-row parsed-predictions)]]))

    (defonce parsed-predictions-response (download-and-parse-predictions))

    (render-html-to-png!
     (str (render-page parsed-predictions-response))
     "/tmp/fillova.png" 6 900)))
