(ns menu-fillova.webrender
  (:require [babashka.fs :as bfs])

  (:import #_[java.awt Font]
   [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]
           [org.xhtmlrenderer.swing Java2DRenderer]))

;;  (def ttf-font-path "resources/fonts/weathericons-regular-webfont.ttf")
;;  (def font (java.awt.Font/createFont java.awt.Font/TRUETYPE_FONT (clojure.java.io/input-stream (clojure.java.io/file ttf-font-path))))

(defn render-html-to-png! [html-str png-filename width height]
  (let [input-file-path (.toString (bfs/create-temp-file))
        _ (spit (File. input-file-path) html-str)
        renderer (Java2DRenderer. input-file-path width height)
        shared-context (-> renderer .getSharedContext)
        _text-renderer (doto (-> shared-context .getTextRenderer)
                         (.setSmoothingThreshold 0))
          ;;  font-resolver (-> renderer .getSharedContext .getFontResolver)
          ;;  _ (def sc shared-context)
          ;;  _ (def fr font-resolver)
          ;;  _ (.setFontMapping sc "weathericons-regular-webfont.ttf" font)
        output-file-path (.toString (bfs/create-temp-file))
        output-file (File. output-file-path)
        rendered-image (.getImage renderer)
        grayscale-image (BufferedImage. width height BufferedImage/TYPE_BYTE_GRAY)]
    (-> grayscale-image .getGraphics (.drawImage rendered-image 0 0 nil))
    (ImageIO/write grayscale-image "png" output-file)
    (bfs/move output-file-path png-filename {:replace-existing true})
    (bfs/delete-if-exists input-file-path)
    (bfs/delete-if-exists output-file-path)))
