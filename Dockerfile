FROM openjdk:17-jdk-slim

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN ./gradlew build --no-daemon

COPY . .

CMD ["java", "-jar", "build/libs/trade-bot-2.0-SNAPSHOT.jar"]
