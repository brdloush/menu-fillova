FROM eclipse-temurin:21.0.2_13-jdk
RUN apt-get update && \
    apt-get install -yq tzdata && \
    ln -fs /usr/share/zoneinfo/Europe/Prague /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata && \
    rm -rf /var/lib/apt/lists/*
RUN mkdir /opt/menu-fillova
ADD target/menu-fillova-1.0.0-standalone.jar /opt/menu-fillova/
EXPOSE 8080
WORKDIR /tmp
ENV RESOURCES_DIR=/opt/menu-fillova/resources
ENV TZ=Europe/Prague
COPY resources /opt/menu-fillova/resources
CMD ["/opt/java/openjdk/bin/java", "-jar", "/opt/menu-fillova/menu-fillova-1.0.0-standalone.jar"]
