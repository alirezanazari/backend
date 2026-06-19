FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

ARG SERVICE

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY contract/pom.xml contract/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY iam-service/pom.xml iam-service/pom.xml
COPY account-service/pom.xml account-service/pom.xml
COPY ledger-service/pom.xml ledger-service/pom.xml
COPY card-service/pom.xml card-service/pom.xml
COPY kyc-service/pom.xml kyc-service/pom.xml
COPY feature-flag-service/pom.xml feature-flag-service/pom.xml
COPY transfer-service/pom.xml transfer-service/pom.xml
COPY pfm-service/pom.xml pfm-service/pom.xml

RUN ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY . .

RUN test -n "$SERVICE"
RUN ./mvnw -B -ntp -pl "$SERVICE" -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ARG SERVICE

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

RUN groupadd --system redbank && useradd --system --gid redbank --home-dir /app redbank

COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar

USER redbank

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
