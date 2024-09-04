(ns menu-fillova.scratch
  (:require [org.httpkit.client :as client]
            [cheshire.core :as json]))


(->> @(client/get "https://jsonplaceholder.typicode.com/todos")
     )
