FROM amazoncorretto:17

WORKDIR /app

COPY trade-bot-2.0-SNAPSHOT.jar .

# HTML pages served by Spring Boot (grid-analysis, main, orders)
COPY pages ./pages

CMD ["java", "-jar", "trade-bot-2.0-SNAPSHOT.jar"]
