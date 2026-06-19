FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/streaming-engine-0.1.0-SNAPSHOT.jar app.jar

ENV JAVA_TOOL_OPTIONS=""
EXPOSE 8085
ENTRYPOINT ["java"]
CMD ["-jar", "app.jar"]
