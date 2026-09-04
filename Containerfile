FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest
WORKDIR /deployments
COPY --from=build /workspace/target/*.jar /deployments/app.jar
EXPOSE 8083
USER 185
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/app.jar"]
