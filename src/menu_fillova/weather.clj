(ns menu-fillova.weather
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [hiccup2.core :as h]
            [menu-fillova.css :as css]
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

(defn weather-code->png-image [code]
  (cond
    (= code 0) "wi-day-sunny.png"
    (#{1} code) "wi-day-cloudy.png"
    (#{2} code) "wi-cloud.png"
    (#{3} code) "wi-cloudy.png"
    (#{45} code) "wi-fog.png"
    (#{48} code) "wi-fog.png"
    (#{51} code) "wi-day-sprinkle.png"
    (#{53} code) "wi-day-sprinkle.png"
    (#{55} code) "wi-day-sprinkle.png"
    (#{56} code) "wi-day-sleet.png"
    (#{57} code) "wi-day-sleet.png"
    (#{61} code) "wi-day-rain.png"
    (#{63} code) "wi-day-rain.png"
    (#{65} code) "wi-day-rain.png"
    (#{66} code) "wi-day-sleet.png"
    (#{67} code) "wi-day-sleet.png"
    (#{71} code) "wi-day-snow.png"
    (#{73} code) "wi-day-snow.png"
    (#{75} code) "wi-day-snow.png"
    (#{77} code) "wi-day-hail.png"
    (#{80} code) "wi-day-showers.png"
    (#{81} code) "wi-day-showers.png"
    (#{82} code) "wi-day-showers.png"
    (#{85} code) "wi-day-snow-wind.png"
    (#{86} code) "wi-day-snow-wind.png"
    (#{95} code) "wi-day-thunderstorm.png"
    (#{96} code) "wi-day-thunderstorm.png"
    (#{99} code) "wi-day-thunderstorm.png"
    :else "wi-na.png"))

(defn get-weather-description [code]
  (cond
    (= code 0) "Jasno"
    (#{1} code) "Polojasno"
    (#{2} code) "Oblačno"
    (#{3} code) "Zataženo"
    (#{45} code) "Mlha"
    (#{48} code) "Námraza"
    (#{51} code) "Slabé mrholení"
    (#{53} code) "Střední mrholení"
    (#{55} code) "Husté mrholení"
    (#{56} code) "Slabé mrz. mrholení."
    (#{57} code) "Silné mrz. mrholení"
    (#{61} code) "Slabý déšť"
    (#{63} code) "Střední déšť"
    (#{65} code) "Silný déšť"
    (#{66} code) "Slabý mrz. déšť"
    (#{67} code) "Silný mrz. déšť"
    (#{71} code) "Slabé sněžení"
    (#{73} code) "Střední sněžení"
    (#{75} code) "Silné Sněžení"
    (#{77} code) "Krupobití"
    (#{80} code) "Slabý déšť se sprškami"
    (#{81} code) "Střední déšť se sprškami"
    (#{82} code) "Prudký déšť se sprškami"
    (#{85} code) "Slabé sněhové přeháňky"
    (#{86} code) "Silné sněhové přeháňky"
    (#{95} code) "Slabá bouřka"
    (#{96} code) "Střední bouřka"
    (#{99} code) "Bouřka s těžkým krupobitím"
    :else "Neznámý kód"))

(defn resources-dir-location []
  (or (System/getenv "RESOURCES_DIR")
      (str (fs/path (.toString (fs/cwd)) "resources"))))

(defn render-prediction [{:keys [time
                                 weather_code
                                 temperature_2m
                                 precipitation
                                 precipitation_probability
                                 wind_speed_10m] :as _prediction}]
  (let [weather-icon-png (str "file://" (resources-dir-location) "/weather-icons/png/"
                              (weather-code->png-image weather_code))
        weather-description (get-weather-description weather_code)]
    [:div {:style {:background-color "#FFF"
                   :float "left"
                   :width "200px"
                   #_#_:padding "12px"}}
     [:div {:style {:height "80px"}}
      [:center
       [:img {:src weather-icon-png
              :style {:background-color "#FFF"}
              :width "80px"}]]]
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

(defn render-predictions-row [parsed-predictions]
  (let [plus-days 0
        hours-offset (* plus-days 24)]
    [:div {:style {:width "100%"}}
     (render-prediction (nth parsed-predictions (+ hours-offset 8)))
     (render-prediction (nth parsed-predictions (+ hours-offset 12)))
     (render-prediction (nth parsed-predictions (+ hours-offset 16)))]))

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
     "/tmp/fillova.png" 600 900)))