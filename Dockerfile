# Stage 1: Build JAR bang Maven Wrapper (Java 21)
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime nhe (kem fontconfig/ttf-dejavu cho JasperReports PDF)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache fontconfig ttf-dejavu curl

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
