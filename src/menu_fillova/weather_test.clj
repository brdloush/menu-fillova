(ns menu-fillova.weather-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [menu-fillova.weather :as subject]
   [cheshire.core :as json]))

(deftest parse-prediction-response-test
  (let [parsed-json-response (json/parse-string (slurp (io/resource "test-data/open-meteo-forecast-response.json")) true)
        result (subject/parse-prediction-response parsed-json-response)]
    (is (= 48 (count result)))
    (is (= {:time "2024-09-02T00:00",
            :temperature_2m 20.4,
            :relative_humidity_2m 74,
            :precipitation_probability 0,
            :precipitation 0.0,
            :weather_code 1,
            :wind_speed_10m 6.1}
           (first result)))))
