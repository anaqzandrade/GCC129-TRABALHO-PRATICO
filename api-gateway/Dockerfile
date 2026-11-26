# syntax=docker/dockerfile:1

# ---------- Estágio 1: build (JDK + Maven; não vai para a imagem final) ----------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Copia só o pom primeiro: enquanto as dependências não mudarem, esta camada fica em cache
# e um rebuild depois de mexer no código não baixa tudo de novo.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Empacota e separa o jar em camadas: dependências (mudam pouco) e código da aplicação (muda sempre).
# O mkdir garante que as 4 pastas existam mesmo quando alguma camada vem vazia.
RUN mvn -B -q -DskipTests package \
 && cp target/api-gateway-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted \
 && mkdir -p extracted/dependencies extracted/spring-boot-loader \
             extracted/snapshot-dependencies extracted/application

# ---------- Estágio 2: runtime (só JRE, sem Maven, sem código-fonte) ----------
FROM eclipse-temurin:21-jre-alpine

# não roda como root dentro do container
RUN addgroup -S ravita && adduser -S ravita -G ravita
WORKDIR /application

# uma camada por grupo: mudar só o código reconstrói só a última
COPY --from=build --chown=ravita:ravita /build/extracted/dependencies/ ./
COPY --from=build --chown=ravita:ravita /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=ravita:ravita /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=ravita:ravita /build/extracted/application/ ./

USER ravita
EXPOSE 8000

# respeita o limite de memória do container em vez da memória da máquina
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "application.jar"]
