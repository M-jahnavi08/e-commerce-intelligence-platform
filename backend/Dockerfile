FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S commerce && adduser -S commerce -G commerce
WORKDIR /app
COPY --from=build /app/target/intelligence-0.1.0.jar app.jar
COPY --chmod=644 rds-ca.pem /app/rds-ca.pem
USER commerce
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
