(ns menu-fillova.meal-menu
  (:require
   [babashka.curl :as curl]
   [clojure.string :as str]
   [hickory.core :as hickory]
   [hickory.select :as s]
   [menu-fillova.time :refer [format-czech-datetime is-working-day?
                              prague-time!]])
  (:import
   [java.time
    DayOfWeek
    LocalDate
    LocalTime
    ZoneId
    ZoneOffset]
   [java.util Date]))

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

(comment
  (def menu-hickory (download-menu-hickory! (second menu-urls))))

(defn extract-week-title-variant-1 [menu-hickory]
  (->> menu-hickory
       (s/select (s/tag :h1))
       first :content first :content first))

(comment
  (extract-week-title-variant-1 menu-hickory))

(defn extract-week-title-variant-2 [menu-hickory]
  (-> (->> menu-hickory
           (s/select (s/class "huge-text"))
           (filter (fn [e]
                     (:content e)))
           (map :content)
           (map first)
           (apply str))
      (str/replace " " "")))

(comment
  (extract-week-title-variant-1 menu-hickory))

(defn extract-week-title [menu-hickory]
  (or (extract-week-title-variant-1 menu-hickory)
      (extract-week-title-variant-2 menu-hickory)))

(comment
  (def week-title (extract-week-title menu-hickory)))

(defn parse-czech-date [czech-date-str]
  (.parse (java.text.SimpleDateFormat. "d.M.yyyy")
          czech-date-str))

(defn extract-until-date [week-title]
  (->> (re-find #"(\d+\.\d+\.\d{4})$" week-title)
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

(defn download-current-menu! []
  (let [prague-time (prague-time!)
        is-within-this-week (let [[week-start-inst week-end-inst] (current-week-inst-ranges!)]
                              (fn [inst]
                                (is-inst-between? inst week-start-inst week-end-inst)))
        parsed-menus (->> (pmap
                           (fn [url]
                             (let [menu-hickory (download-menu-hickory! url)
                                   week-title (extract-week-title menu-hickory)
                                   until-date (extract-until-date week-title)]
                               {:url url
                                :menu-hickory menu-hickory
                                :week-title week-title
                                :until-date until-date}))
                           menu-urls))]
    (or (when (is-working-day? prague-time)
          (first (filter (fn [{:keys [until-date]}]
                           (is-within-this-week until-date))
                         parsed-menus)))
        (first (sort-by (comp #(.getTime %) :until-date) > parsed-menus)))))

(comment
  (def current-menu (download-current-menu!)))

(defn extract-days [cleaned-up-lunch-rows-strings]
  (let [singleline-text (->> cleaned-up-lunch-rows-strings
                             (interleave (repeat "\n"))
                             (apply str))]
    {:monday (second (re-find #"(?is)Pondělí[ :]*(.+)Úterý.*" singleline-text))
     :tuesday (second (re-find #"(?is)Úterý[ :]*(.+)Středa.*" singleline-text))
     :wednesday (second (re-find #"(?is)Středa[ :]*(.+)Čtvrtek.*" singleline-text))
     :thursday (second (re-find #"(?is)Čtvrtek[ :]*(.+)Pátek.*" singleline-text))
     :friday (second (re-find #"(?is)Pátek[ :]*(.+)" singleline-text))}))

(defn blank-or-nbsp-str? [s]
  (when (string? s)
    (or (str/blank? s)
        (= " " s))))

(comment
  (blank-or-nbsp-str? " ")
  ;; => true
  )

(defn blank-element? [e tag]
  (and (map? e)
       (= :element (:type e))
       (or (nil? tag)
           (= tag (:tag e)))
       (every? #(or (blank-element? % nil)
                    (blank-or-nbsp-str? %))
               (:content e))))
(comment
  (blank-element? {:type :element, :attrs nil, :tag :strong, :content [" "]} :strong))

(defn cleanup-lunch-rows [menu-hickory]
  (->> (s/select (s/tag :p) menu-hickory)
       (map (fn [p-el]
              (some->> (:content p-el)
                       (remove (fn [content]
                                 (or (blank-or-nbsp-str? content)
                                     (blank-element? content nil))))
                       first)))
       (remove nil?)
       (map (fn [e]
              (if (map? e)
                (first (:content e))
                e)))
       (remove nil?)
       (remove #(re-matches #"^Úvod.*$" %))
       (remove #(re-matches #"^Jídelníček.*$" %))
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
       (remove #(= "" %))))

(comment
  (def menu-hickory (:menu-hickory (download-current-menu!)))
  (cleanup-lunch-rows menu-hickory)
  (extract-days (cleanup-lunch-rows menu-hickory)))

(defn make-model! []
  (let [{:keys [menu-hickory]} (download-current-menu!)]
    (when menu-hickory
      (let [title (extract-week-title menu-hickory)
            cleaned-up-lunch-rows (cleanup-lunch-rows menu-hickory)]
        {:week-title title
         :days (extract-days cleaned-up-lunch-rows)}))))

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
       (map (fn [[day-kw day-text]]
              [:div
               [:div {:style {:font-size "20pt"
                              :padding-top "16pt"
                              :font-weight 800}}
                (day-labels day-kw)]
               [:div {:style {:padding-top "4pt"
                              :font-size "18pt"}}
                (->> (str/split (or day-text "") #"\n")
                     (map #(-> [:div %])))]])
            days)]]
     [:div {:style {:text-align "right"
                    :padding-top "12pt"
                    :font-size "12pt"}} (current-datetime)]]))

(comment
  (render {:week-title "Jídelníček od 15.4. do 19.4.2024",
           :days
           {:monday "\npol.zeleninová snoky\nčočka se zakysanou smetanou,veka,čaj\n",
            :tuesday "\noběd :pol.hráškové pyré\n",
            :wednesday "\npol.jáhlová\nkuř.plátek na česneku s rýží,čaj\n",
            :thursday "\npol.z vaječné jíšky\ntreska sbrokolicí,br.kaše,čaj\n",
            :friday "\npol.kapustová\nšpagety po milánsku, sýr,čaj"}}))
