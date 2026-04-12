FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/streaming-engine-0.1.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS=""

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
