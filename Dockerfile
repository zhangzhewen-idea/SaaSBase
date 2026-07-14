FROM alibabadragonwell/dragonwell:25.0.3.0.3.9-standard-ga-anolis

WORKDIR /app
COPY target/saasbase-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/app.jar"]
