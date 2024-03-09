(ns menu-fillova.time
  (:require [tick.core :as t]) 
  (:import [java.text SimpleDateFormat]
           [java.time DayOfWeek]))

(defn prague-time! []
  (-> (t/date-time)
      (t/in "Europe/Prague")))

(defn format-czech-date [date]
  (.format (SimpleDateFormat. "d.M.y")
           date))

(def czech-name-of-days 
  {DayOfWeek/SUNDAY "Neděle"
   DayOfWeek/MONDAY "Pondělí"
   DayOfWeek/TUESDAY "Úterý"
   DayOfWeek/WEDNESDAY "Středa"
   DayOfWeek/THURSDAY "Čtvrtek"
   DayOfWeek/FRIDAY "Pátek"
   DayOfWeek/SATURDAY "Sobota"})

(defn czech-day-of-week [zdt]
  (let [dow (t/day-of-week zdt)
        czech-name (get czech-name-of-days dow)] 
    czech-name))

(defn is-weekend? [zdt]
  (some? (#{DayOfWeek/SATURDAY
            DayOfWeek/SUNDAY}
          (t/day-of-week zdt))))