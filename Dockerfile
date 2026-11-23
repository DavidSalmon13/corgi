# One image for Railway: the Vite web app is built and served as static files by the Spring Boot API,
# so the SPA and /api share an origin (the session cookie works without CORS), same as the dev proxy.

# 1. Web app
FROM node:24-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# 2. API, with the web build bundled into its static resources
FROM eclipse-temurin:21-jdk AS api
WORKDIR /api
COPY api/gradlew api/settings.gradle.kts api/build.gradle.kts ./
COPY api/gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY api/src src
COPY --from=web /web/dist/ src/main/resources/static/
RUN ./gradlew --no-daemon bootJar -x test \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

# 3. Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /app.jar app.jar
# Railway sets PORT; application.yml reads it (defaults to 8080).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
