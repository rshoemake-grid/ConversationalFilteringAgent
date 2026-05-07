# Build stage
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

COPY . .
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/target/conversational-filtering-agent-*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV APP_SERVE_FRONTEND=false

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
