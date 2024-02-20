(ns menu-fillova.core
  (:gen-class)
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hiccup2.core :as h]
            [org.httpkit.server :as http]
            [hickory.core :as hickory]
            [hickory.select :as s]))

(def port 8080)

(defonce compose-tmp (name (gensym "compose-")))

(def menu-urls ["https://www.ms-fillova.cz/jidelnicek1-stary"
                "https://www.ms-fillova.cz/jidelnicek2-stary"])

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

(defn download-newest-menu [menu-urls]
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
       (sort-by (comp #(.getTime %) :until-date) >)
       first))

(defn extract-days [cleaned-up-lunch-rows-strings]
  (let [singleline-text (->> cleaned-up-lunch-rows-strings
                             (interleave (repeat "\n"))
                             (apply str))]
    {:monday (second (re-find #"(?is)(Pondělí.+)Úterý." singleline-text))
     :tuesday (second (re-find #"(?is)(Úterý.+)Středa.+" singleline-text))
     :wednesday (second (re-find #"(?is)(Středa.+)Čtvrtek.+" singleline-text))
     :thursday (second (re-find #"(?is)(Čtvrtek.+)Pátek.+" singleline-text))
     :friday (second (re-find #"(?is)(Pátek.+)" singleline-text))}))

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
           (map #(str/replace % #"(svač.:).+$" ""))
           (map #(str/replace % #"(oběd:)" ""))
           (map str/trim)
           (remove #(= "" %)))))

(defn make-model [menu-hickory]
  (let [title (extract-week-title menu-hickory)
        cleaned-up-lunch-rows (cleanup-lunch-rows menu-hickory)]
    {:week-title title
     :days (extract-days cleaned-up-lunch-rows)}))

(defn make-day-names-bold [s]
  (reduce (fn [acc i]
            (str/replace acc (re-pattern i) (str "<b>" i "</b>")))
          s
          ["Pondělí" "Úterý" "Středa" "Čtvrtek" "Pátek"]))

(defn current-datetime []
  (.format (java.text.SimpleDateFormat. "d.M.yyyy, HH:mm:ss") (java.util.Date.)))

(defn postprocess-day-text [s]
  (make-day-names-bold s))

(defn render-pango [{:keys [week-title days] :as _model}]
  [:span {:font-family "DejaVu Serif"}
   [:span {:font_size 22000} week-title]  
   [:span {:font-size 12000} "\n"]
   [:span
    (map (fn [[_day-kw day-text]]
           [:span
            [:span {:font_size 25000} "\n"]
            [:span {:font_size 15000} (postprocess-day-text day-text)]])
         days)]
   [:span "\n\n"]
   [:span {
           :font_size 10000} (current-datetime)]])

(defn compose [output-path {:keys [menu-hickory]}]
  (let [output-dir (-> output-path fs/file fs/parent)]
    (when output-dir
      (fs/create-dirs output-dir))
    (let [tmp-dir (fs/path (fs/temp-dir) compose-tmp)
          pango-path (str (fs/path tmp-dir "pango.png"))]
      (fs/create-dirs tmp-dir)
      (println "Writing pango output to: " pango-path)
      (p/sh "convert" "-background" "white" "-size" "600x800"
            (str "pango:" (h/html (render-pango (make-model menu-hickory))))
            "-bordercolor" "white" "-border" "30"
            pango-path)

      (println "Writing result output to: " output-path)
      (p/sh "montage" pango-path
            "-tile" "x2" "-mode" "concatenate"
            output-path)
      nil)))

(defn upload-to-kindle []
  (p/sh "scp" "output/composition.png" "root@kindle:/tmp/image.png"))

(defn show-on-kindle []
  (p/sh "ssh" "root@kindle" "/usr/sbin/eips -g /tmp/image.png"))

(defn compose-file []
  (compose "output/composition.png" (download-newest-menu menu-urls)))

(defn go []
  (compose-file)
  (upload-to-kindle)
  (show-on-kindle)
  nil)

(comment
  (go))

(defn handler [_req]
  {:status 200
   :body (let [_ (compose-file)]
           (io/file "output/composition.png"))})

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

(defn -main [& _args]
  (start-server port)
  (deref (promise)))

;; (go)