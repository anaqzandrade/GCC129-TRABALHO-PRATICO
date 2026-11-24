FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# pom.xml primeiro: as dependências ficam numa camada em cache e só são
# baixadas de novo quando o pom muda, não a cada alteração no código
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
# a aplicação não roda como root dentro do container
RUN groupadd --system ravita && useradd --system --gid ravita ravita
WORKDIR /app
COPY --from=build --chown=ravita:ravita /app/target/organization-service-0.0.1-SNAPSHOT.jar app.jar
USER ravita
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
