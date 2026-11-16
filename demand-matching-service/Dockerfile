FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# pom.xml primeiro: as dependências ficam numa camada em cache e só são
# baixadas de novo quando o pom muda, não a cada alteração no código
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
# a aplicação não roda como root dentro do container
RUN addgroup -S ravita && adduser -S ravita -G ravita
WORKDIR /app
COPY --from=build --chown=ravita:ravita /app/target/*.jar app.jar
USER ravita
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
