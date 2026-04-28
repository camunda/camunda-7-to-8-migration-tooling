FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/migration-example-camunda8-result-*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
