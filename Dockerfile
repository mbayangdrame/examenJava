FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/chat-server.jar /app/chat-server.jar

EXPOSE 5555

CMD ["java", "-jar", "chat-server.jar"]
