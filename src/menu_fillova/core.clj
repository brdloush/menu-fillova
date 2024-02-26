(ns menu-fillova.core
  (:gen-class)
  (:require [babashka.curl :as curl]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as h]
            [hickory.core :as hickory]
            [hickory.select :as s]
            [menu-fillova.css :as css]
            [menu-fillova.weather :as weather]
            [menu-fillova.webrender :as wr]
            [org.httpkit.server :as http])
  (:import [java.time
            DayOfWeek
            LocalDate
            LocalTime
            ZoneId
            ZoneOffset]
           [java.util Date]))

(def port 8080)

(def menu-urls ["https://www.ms-fillova.cz/jidelnicek1-stary"
                "https://www.ms-fillova.cz/jidelnicek2-stary"])

(def day-labels {:monday "Pondělí"
                 :tuesday "Úterý"
                 :wednesday "Středa"
                 :thursday "Čtvrtek"
                 :friday "Pátek"})

(defn download-menu-hickory! [url]
  (-> (curl/get url)
      :body
      str/trim
      (hickory/parse)
      (hickory/as-hickory)))

(defn extract-week-title-variant-1 [menu-hickory]
  (->> menu-hickory
       (s/select (s/tag :h1))
       first :content first :content first))

(defn extract-week-title-variant-2 [menu-hickory]
  (-> (->> menu-hickory
           (s/select (s/class "huge-text"))
           (filter (fn [e]
                     (:content e)))
           (map :content)
           (map first)
           (apply str))
      (str/replace " " "")))

(defn extract-week-title [menu-hickory]
  (or (extract-week-title-variant-1 menu-hickory)
      (extract-week-title-variant-2 menu-hickory)))

(defn parse-czech-date [czech-date-str]
  (.parse (java.text.SimpleDateFormat. "d.M.yyyy")
          czech-date-str))

(defn extract-until-date [menu-title]
  (->> (re-find #" (\d+\.\d+\.\d{4})$" menu-title)
       second
       parse-czech-date))

(defn is-inst-between? [inst low-inst high-inst]
  (->> [low-inst inst high-inst]
       (map #(.getTime %))
       (apply <)))

(defn current-week-inst-ranges! []
  (let [now-inst (.toInstant (Date.))
        now-ld (LocalDate/ofInstant now-inst ZoneOffset/UTC)]
    [(-> now-ld (.with DayOfWeek/MONDAY) .atStartOfDay (.atZone (ZoneId/of "Europe/Prague")) .toInstant Date/from)
     (-> now-ld (.with DayOfWeek/SUNDAY) (.atTime LocalTime/MAX) (.atZone (ZoneId/of "Europe/Prague")) .toInstant Date/from)]))

(let [now-inst (.toInstant (Date.))
      now-ld (LocalDate/ofInstant now-inst ZoneOffset/UTC)]
  (-> now-ld (.with DayOfWeek/MONDAY) .atStartOfDay (.atZone (ZoneId/of "Europe/Prague")) .toInstant Date/from))

(defn download-current-menu [menu-urls]
  (let [is-within-this-week (let [[week-start-inst week-end-inst] (current-week-inst-ranges!)]
                              (fn [inst]
                                (is-inst-between? inst week-start-inst week-end-inst)))]
    (->> (pmap
           (fn [url]
             (let [menu-hickory (download-menu-hickory! url)
                   week-title (extract-week-title menu-hickory)
                   until-date (extract-until-date week-title)]
               {:url url
                :menu-hickory menu-hickory
                :week-title week-title
                :until-date until-date}))
           menu-urls)
         (filter (fn [{:keys [until-date]}]
                   (is-within-this-week until-date)))
         first)))

(defn extract-days [cleaned-up-lunch-rows-strings]
  (let [singleline-text (->> cleaned-up-lunch-rows-strings 
                             (interleave (repeat "\n"))
                             (apply str))]
    {:monday (second (re-find #"(?is)Pondělí[ :]*(.+)Úterý." singleline-text))
     :tuesday (second (re-find #"(?is)Úterý[ :]*(.+)Středa.+" singleline-text))
     :wednesday (second (re-find #"(?is)Středa[ :]*(.+)Čtvrtek.+" singleline-text))
     :thursday (second (re-find #"(?is)Čtvrtek[ :]*(.+)Pátek.+" singleline-text))
     :friday (second (re-find #"(?is)Pátek[ :]*(.+)" singleline-text))}))

(defn cleanup-lunch-rows [menu-hickory]
  (-> menu-hickory
      (->> (s/select (s/and (s/tag :p)))
           (filter #(-> % :content first string?))
           (map #(-> % :content first))
           (remove #(re-matches #"^[  ]+$" %))
           (remove #(re-matches #".*alergeny.*" %))
           (remove #(re-matches #".*Změna jídelníčku.*" %))
           (remove #(re-matches #"(?i).*STRAVA JE.*" %))
           (remove #(re-matches #"^\n.+" %))
           (map #(str/replace % " " ""))
           (map #(str/replace % #"\d[0-9abcde, ]+$" ""))
           (map #(str/replace % #"(svač.?:).+$" ""))
           (map #(str/replace % #"(oběd:)" ""))
           (map str/trim)
           (remove #(= "" %)))))

(defn make-model [menu-hickory]
  (let [title (extract-week-title menu-hickory)
        cleaned-up-lunch-rows (cleanup-lunch-rows menu-hickory)]
    {:week-title title
     :days (extract-days cleaned-up-lunch-rows)}))

(defn current-datetime []
  (.format (java.text.SimpleDateFormat. "d.M.yyyy, HH:mm:ss") (java.util.Date.)))

(defn compose-file-fs [{:keys [menu-hickory]}] 
  (let [width 600
        height 800
        model (make-model menu-hickory)
        {:keys [week-title days]} model
        html (str (h/html
                   [:html
                    [:head
                     [:style css/reset]]
                    [:div
                     [:div {:style {:font-size "20pt;"
                                    :font-family "DejaVu Serif"}}

                      ;; Menu fillova
                      [:div {:style {:padding "32pt"
                                     :height "550px"
                                     :overflow "hidden"}}
                       [:div {:style {:font-size "24pt"
                                      :font-weight 800}}
                        [:center week-title]]
                       [:hr {:style {:border "1px dotted black"}}]
                       [:div
                        (map (fn [[day-kw day-text]]
                               [:div 
                                [:div {:style {:font-size "22pt"
                                               :padding-top "16pt"
                                               :font-weight 800}} 
                                 (day-labels day-kw)]
                                [:div {:style {:padding-top "4pt"
                                               :font-size "20pt"}}
                                 (->> (str/split day-text #"\n")
                                      (map #(-> [:div %])))]])
                             days)]
                       [:div {:style {:text-align "right"
                                      :padding-top "12pt"
                                      :font-size "14pt"}} (current-datetime)]]
                      
                      (weather/render-predictions-row (weather/download-and-parse-predictions))]]]))]
    (wr/render-html-to-png! html "/tmp/fillova.png" width height))
  )

;; server
(defn handler [_req]
  {:status 200
   :body (let [_ (compose-file-fs (download-current-menu menu-urls))]
           (io/file "/tmp/fillova.png"))})

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
(def memoized-download-current-menu (memoize download-current-menu))

(defn upload-to-kindle []
  (p/sh "scp" "/tmp/fillova.png" "root@kindle:/tmp/image.png"))

(defn show-on-kindle []
  (p/sh "ssh" "root@kindle" "/usr/sbin/eips -g /tmp/image.png"))

(defn go []
  (compose-file-fs (memoized-download-current-menu menu-urls))
  (upload-to-kindle)
  (show-on-kindle)
  nil)

(comment
  (compose-file-fs (memoized-download-current-menu menu-urls)))
