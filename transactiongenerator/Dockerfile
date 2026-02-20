FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ ./src/
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-jammy
EXPOSE 8080
WORKDIR /app
COPY --from=build /build/target/transactiongenerator*.jar application.jar
ENTRYPOINT ["java", "-jar", "application.jar"]