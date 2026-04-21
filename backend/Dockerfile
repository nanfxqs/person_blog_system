FROM maven:3.9.7-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
