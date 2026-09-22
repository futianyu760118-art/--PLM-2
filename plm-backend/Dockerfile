FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q || true
COPY src ./src
RUN mvn package -DskipTests -q && \
    mv target/plm-backend.jar app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/app.jar .
RUN mkdir -p /data/files/intranet /data/files/extranet /data/files/temp
EXPOSE 8080
ENV SERVER_PORT=8080
CMD ["java", "-jar", "app.jar"]
