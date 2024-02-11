FROM babashka/babashka:latest

RUN apt-get update && apt-get install -y imagemagick && rm -rf /var/lib/apt/lists/*

# pre-download bootleg dependency on build time
RUN bb -e "(do (require ['babashka.pods :as 'pods]) (pods/load-pod 'retrogradeorbit/bootleg \"0.1.9\"))"

RUN mkdir /opt/menu-fillova
ADD menu_fillova.clj /opt/menu-fillova/
EXPOSE 8080
WORKDIR /tmp

CMD ["/usr/local/bin/bb", "/opt/menu-fillova/menu_fillova.clj"]
