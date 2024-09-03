(ns menu-fillova.meal-menu-mk2
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [org.httpkit.client :as http]
   [pdfboxing.text :as text]
   [menu-fillova.time :refer [format-czech-datetime
                              prague-time!]])
  (:import
   [java.time
    DayOfWeek
    Instant
    LocalDate
    LocalTime
    ZoneId
    ZoneOffset]
   [java.util Date]))

(def ^:private msrysanka-meal-menu-page-url
  "https://www.msrysanka.cz/jidelnicek")

(defn get-meal-menu-url! []
  (re-find #"https://.+\.pdf" (slurp msrysanka-meal-menu-page-url)))

(defn download-meal-menu-txt! [url]
  (when-let [response @(http/get url)]
    (let [temp-file (java.io.File/createTempFile "fillova-menu" ".pdf")]
      (with-open [input-stream (:body response)
                  output-stream (io/output-stream temp-file)]
        (try
          (io/copy input-stream output-stream)
          (.flush output-stream)
          (-> (text/extract temp-file)
              (str/trim)
              (str/replace #" +" " ")
              (str/replace "–" "-"))

          (finally
            (fs/delete-if-exists temp-file)))))))

(comment
  (download-meal-menu-txt! (get-meal-menu-url!)))

(defn extract-week [s]
  (when s
    (some-> (re-find #"Týden:\s*(.+\d{4})" s)
            second
            (str/replace #"\s*" "")
            (str/replace "-" " - ")
            (str/replace "–" " - "))))

(defn extract-days [week-lines]
  (->> week-lines
       (remove #(re-find #"Přesnídávka|Svačina|seznam-alergenu" %)) ;; we're only interested in Polévka and Jídlo lines
       (map #(str/replace % #"alergeny:" "")) ;; get rid of alergens title label
       (map str/trim)
       (map #(str/replace % #"\s[0-9,]+$" "")) ;; get rid of trailing alergens info on each meal line
       (map str/trim)
       (partition-by (fn [line]
                       (re-find #"PONDĚLÍ|ÚTERÝ|STŘEDA|ČTVRTEK|PÁTEK" line)))
       (partition 2)
       (map (fn [[day-lines meal-lines]]
              {:day (first day-lines)
               :meal-lines (->> meal-lines
                                (map #(str/replace % #"Polévka:\s+|Jídlo:\s+" "")))}))))
(defn parse-czech-date-inst [czech-date-str]
  (when czech-date-str
    (.toInstant (.parse (java.text.SimpleDateFormat. "d.M.yyyy")
                        czech-date-str))))

(defn extract-week-end-inst [s]
  (when s
    (parse-czech-date-inst (re-find #"[0-9.]+$" s))))

(defn is-inst-between? [inst low-inst high-inst]
  (->> [low-inst inst high-inst]
       (map #(.toEpochMilli %))
       (apply <)))

(defn current-week-inst-ranges! []
  (let [now-inst (.toInstant (Date.))
        now-ld (LocalDate/ofInstant now-inst ZoneOffset/UTC)]
    [(-> now-ld (.with DayOfWeek/MONDAY) .atStartOfDay (.atZone (ZoneId/of "Europe/Prague")) .toInstant)
     (-> now-ld (.with DayOfWeek/SUNDAY) (.atTime LocalTime/MAX) (.atZone (ZoneId/of "Europe/Prague")) .toInstant)]))

(defn is-day-before-today! [some-inst]
  (when some-inst
    (let [today-start-day-ldt (-> (LocalDate/ofInstant (Instant/now) ZoneOffset/UTC) .atStartOfDay)
          evaluated-ldt (-> (LocalDate/ofInstant some-inst ZoneOffset/UTC) .atStartOfDay)]
      (.isBefore evaluated-ldt today-start-day-ldt))))

(defn parse-model [pdf-text]
  (let [[current-week-start-inst current-week-end-inst] (current-week-inst-ranges!)]
    (->> (str/split-lines pdf-text)
         (remove #(str/blank? %))
         (partition-by (fn [line]
                         (re-find #"^\s*JÍDELNÍČEK" line)))
         (partition 2)
         (map (fn [[week-headings week-lines]]
                (let [week-str (extract-week (first week-headings))
                      week-end-inst (extract-week-end-inst week-str)]
                  {:week-title week-str
                   :week-finish-inst week-end-inst
                   :is-in-past (is-day-before-today! week-end-inst)
                   :is-current-week (is-inst-between? week-end-inst current-week-start-inst current-week-end-inst)
                   :days (extract-days week-lines)}))))))

(defn make-model! []
  (let [pdf-text (download-meal-menu-txt! (get-meal-menu-url!))]
    (->> (parse-model pdf-text)
         (remove #(:is-in-past %))
         first)))

(defn current-datetime []
  (format-czech-datetime (prague-time!)))

(defn render [model]
  (when-let [{:keys [week-title days]} model]
    [:div
     [:div
      [:div {:style {:font-size "24pt"
                     :font-weight 800}}
       [:center week-title]]
      [:hr {:style {:border "1px dotted black"}}]
      [:div
       (map (fn [{:keys [day meal-lines] :as _day}]
              [:div
               [:div {:style {:font-size "20pt"
                              :padding-top "16pt"
                              :font-weight 800}}
                day]
               (map (fn [meal-line]
                      [:div {:style {:font-size "16pt"
                                     :font-weight 100
                                     :padding-top "4pt"}}
                       meal-line])
                    meal-lines)])
            days)]]
     [:div {:style {:text-align "right"
                    :padding-top "12pt"
                    :font-size "12pt"}} (current-datetime)]]))

(comment
  (make-model!))
