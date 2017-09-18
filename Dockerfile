FROM maven:3-jdk-8

MAINTAINER tuxmonteiro

ENV VERSION 0.0.1-SNAPSHOT

EXPOSE 8090

COPY . .

RUN apt-get update -y && apt-get install -y make && mvn clean package spring-boot:build-info -DskipTests

CMD make run
