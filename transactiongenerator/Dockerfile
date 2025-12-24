FROM amazoncorretto:23-alpine

EXPOSE 8080

WORKDIR /app

COPY ./target/transactiongenerator*.jar /app/application.jar

ENTRYPOINT [ "java", "-jar", "/app/application.jar" ]