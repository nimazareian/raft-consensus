FROM ubuntu:latest
LABEL authors="boris"

ENTRYPOINT ["top", "-b"]

FROM openjdk:17-oracle
COPY app/build/libs/app.jar /usr/src/app/
COPY app/build/resources/main/config.json /usr/src/app/config.json
WORKDIR /usr/src/app
CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -jar app.jar --config config.json