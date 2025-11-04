FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

COPY settings.gradle.kts build.gradle.kts gradle/ ./
COPY shared/ ./shared/
COPY server/ ./server/

RUN gradle :server:build --no-daemon -x test
RUN find /app/server/build/libs -name "*.jar" -not -name "*-plain.jar" -exec cp {} /app/app.jar \;

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/app.jar app.jar

EXPOSE 8080

ENV PORT=8080

CMD ["java", "-jar", "app.jar"]

