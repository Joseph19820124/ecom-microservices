# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/cart-service.jar /app/app.jar
EXPOSE 8085
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8085/actuator/health || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
