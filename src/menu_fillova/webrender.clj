(ns menu-fillova.webrender
  (:require [babashka.fs :as bfs])
  (:import [java.io File]
           [javax.imageio ImageIO] 
           [org.xhtmlrenderer.swing Java2DRenderer]))

 (defn render-html-to-png! [html-str png-filename width height]
   (let [input-file-path (.toString (bfs/create-temp-file))
         _ (spit (File. input-file-path) html-str)
         renderer (Java2DRenderer. input-file-path width height)
         _ (-> renderer .getSharedContext .getTextRenderer (.setSmoothingThreshold 0))
         output-file-path (.toString (bfs/create-temp-file))
         output-file (File. output-file-path)]
     (ImageIO/write (.getImage renderer) "png" output-file)
     (bfs/move output-file-path png-filename {:replace-existing true})
     (bfs/delete-if-exists input-file-path)
     (bfs/delete-if-exists output-file-path)))
