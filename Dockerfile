FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/chat-server.jar /app/elan-server.jar

EXPOSE 5555

CMD ["java", "-jar", "elan-server.jar"]
