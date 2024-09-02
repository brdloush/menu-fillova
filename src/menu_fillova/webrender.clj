(ns menu-fillova.webrender
  (:require [babashka.fs :as bfs])

  (:import [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]
           [org.xhtmlrenderer.swing Java2DRenderer]))

(defn render-html-to-png! [html-str png-filename width height]
  (let [input-tmp-file-path (.toString (bfs/create-temp-file))
        _ (spit (File. input-tmp-file-path) html-str)
        renderer (Java2DRenderer. input-tmp-file-path width height)
        shared-context (-> renderer .getSharedContext)
        _text-renderer (doto (-> shared-context .getTextRenderer)
                         (.setSmoothingThreshold 0))
        output-tmp-file-path (.toString (bfs/create-temp-file))
        output-tmp-file (File. output-tmp-file-path)
        rendered-image (.getImage renderer)
        grayscale-image (BufferedImage. width height BufferedImage/TYPE_BYTE_GRAY)]
    (-> grayscale-image .getGraphics (.drawImage rendered-image 0 0 nil))
    (ImageIO/write grayscale-image "png" output-tmp-file)
    (bfs/move output-tmp-file-path png-filename {:replace-existing true})
    (bfs/delete-if-exists input-tmp-file-path)
    (bfs/delete-if-exists output-tmp-file-path)))
