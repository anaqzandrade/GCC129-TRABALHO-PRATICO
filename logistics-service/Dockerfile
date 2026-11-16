FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# a aplicação não roda como root dentro do container
RUN addgroup -S ravita && adduser -S ravita -G ravita
WORKDIR /app
COPY --from=builder --chown=ravita:ravita /app/target/*.jar app.jar
USER ravita
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
