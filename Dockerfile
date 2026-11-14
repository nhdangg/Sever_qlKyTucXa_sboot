FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN mkdir -p uploads/avatars

COPY --from=build /app/target/*.war app.war
ENV PORT = 8912
EXPOSE 8912
ENTRYPOINT ["java", "-Xmx380m", "-Xss256k", "-jar", "app.war"]