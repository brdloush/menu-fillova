(ns menu-fillova.webrender
  (:require [babashka.fs :as bfs])
  (:import [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]
           [org.xhtmlrenderer.swing Java2DRenderer]))

 (defn render-html-to-png! [html-str png-filename width height]
   (let [input-file-path (.toString (bfs/create-temp-file))
         _ (spit (File. input-file-path) html-str)
         renderer (Java2DRenderer. input-file-path width height)
         _ (-> renderer .getSharedContext .getTextRenderer (.setSmoothingThreshold 0))
         output-file-path (.toString (bfs/create-temp-file))
         output-file (File. output-file-path)
         rendered-image (.getImage renderer)
         grayscale-image (BufferedImage. width height BufferedImage/TYPE_BYTE_GRAY)]
     (-> grayscale-image .getGraphics (.drawImage rendered-image 0 0 nil))
     (ImageIO/write grayscale-image "png" output-file)
     (bfs/move output-file-path png-filename {:replace-existing true})
     (bfs/delete-if-exists input-file-path)
     (bfs/delete-if-exists output-file-path)))

;;  BufferedImage image_to_save2=new BufferedImage (image_to_save.getWidth (),image_to_save.getHeight (), BufferedImage.TYPE_BYTE_GRAY);
;;  image_to_save2.getGraphics () .drawImage (image_to_save,0,0,null);
;;  image_to_save = image_to_save2; 
