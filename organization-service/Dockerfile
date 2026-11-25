FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
# pom.xml primeiro: as dependências ficam numa camada em cache e só são
# baixadas de novo quando o pom muda, não a cada alteração no código
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# Alpine, como os outros serviços: imagem menor e com o wget usado pelo healthcheck
FROM eclipse-temurin:21-jre-alpine
# a aplicação não roda como root dentro do container
RUN addgroup -S ravita && adduser -S ravita -G ravita
WORKDIR /app
COPY --from=build --chown=ravita:ravita /app/target/organization-service-0.0.1-SNAPSHOT.jar app.jar
USER ravita
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
