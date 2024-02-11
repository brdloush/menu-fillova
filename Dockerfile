FROM eclipse-temurin:21.0.2_13-jdk

RUN apt-get update && apt-get install -y imagemagick && rm -rf /var/lib/apt/lists/*

RUN mkdir /opt/menu-fillova
ADD target/menu-fillova-1.0.0-standalone.jar /opt/menu-fillova/
EXPOSE 8080
WORKDIR /tmp

CMD ["/opt/java/openjdk/bin/java", "-jar", "/opt/menu-fillova/menu-fillova-1.0.0-standalone.jar"]
