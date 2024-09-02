(ns menu-fillova.weather-test
  (:require
   [clojure.test :refer [deftest is]]
   [menu-fillova.weather :as subject]))

(deftest make-model!-test
  (is (= true
         (subject/foo))))
