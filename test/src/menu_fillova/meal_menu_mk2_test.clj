(ns menu-fillova.meal-menu-mk2-test
  (:require
   [clojure.test :refer [deftest is]]
   [menu-fillova.meal-menu-mk2 :as subject]
   [org.httpkit.client :as http]
   [clojure.java.io :as io]))

(def expected-parsed-meal-menu-text
  (delay
    (slurp (io/resource "test-data/expected-parsed-meal-menu-text.txt"))))

(deftest download-meal-menu-txt!-test
  (with-redefs
   [http/get (fn [_url]
               (delay {:status 200
                       :body (io/input-stream (io/resource "test-data/jidelnicek.pdf"))}))]
    (let [actual-parsed-meal-menu-text (subject/download-meal-menu-txt! "http://some.url")]
      (is (= @expected-parsed-meal-menu-text
             actual-parsed-meal-menu-text)))))

(deftest parse-model-test
  (is (= '({:week-title "2.9. - 6.9.2024",
            :week-finish-inst #time/instant "2024-09-05T22:00:00Z",
            :is-in-past false,
            :is-current-week true,
            :days
            ({:day "PONDĚLÍ",
              :meal-lines
              ("dýňový krém se smetanou a černou čočkou"
               "vrtulky s kuřecím masem, rajčaty a bazalkou, sýr, čaj")}
             {:day "ÚTERÝ",
              :meal-lines
              ("zeleninový vývar s písmenky"
               "sekaná pečeně po zahradnicku, brambory, zelný salát, čaj")}
             {:day "STŘEDA",
              :meal-lines
              ("drožďová s vejcem"
               "hovězí maso, znojemská omáčka, luštěninová rýže, čaj")}
             {:day "ČTVRTEK",
              :meal-lines
              ("kuřecí s ovesnými vločkami „ala držťková“"
               "obalovaná treska, bramborová kaše, rajče, čaj")}
             {:day "PÁTEK",
              :meal-lines ("hrachová s krutony" "nudle s mákem, čaj")})}
           {:week-title "9.9. - 13.9.2024",
            :week-finish-inst #time/instant "2024-09-12T22:00:00Z",
            :is-in-past false,
            :is-current-week false,
            :days
            ({:day "PONDĚLÍ:",
              :meal-lines
              ("bramboračka"
               "Oběd: čočka na sladko-kyselo, okurka, vícezrnné pečivo, čaj")}
             {:day "ÚTERÝ",
              :meal-lines
              ("minestrone"
               "Oběd: kuřecí kousky na kari s citronovou trávou, hráškový kuskus, čaj")}
             {:day "STŘEDA",
              :meal-lines
              ("zeleninový vývar s krupičkou a vejcem"
               "Oběd: pečené vepřové maso na česneku, uhlířské brambory, čaj")}
             {:day "ČTVRTEK",
              :meal-lines
              ("fazolová červená" "Oběd: zeleninové rizoto, sýr, řepa, čaj")}
             {:day "PÁTEK",
              :meal-lines
              ("rybí s krutony"
               "Oběd: dušená vepřová kýta na kmíně, brambory, čaj"
               "Paní kuchařky Mš Matěchova: Kristina Demekha, Mariana Tepchuk"
               "Paní kuchařky Mš Fillova: Natália Babinská, Lidiya Saprynyuk Vedoucí ŠJ: Jana Coufalová")})})
         (subject/parse-model @expected-parsed-meal-menu-text))))
