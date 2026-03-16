FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ ./src/
RUN mvn package -DskipTests -B

FROM flink:1.20.1-java17
COPY --from=build /build/target/transactionanalyser-1.0-SNAPSHOT.jar /opt/flink/usrlib/transactionanalyser.jar
