FROM node:22-alpine AS frontend
WORKDIR /web
COPY package*.json ./
RUN npm install
COPY index.html activities.mjs journal.mjs src.jsx Storybooks.jsx art.jsx style.css ./
RUN npm run build
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend .
COPY --from=frontend /web/dist src/main/resources/static
RUN mvn -B package -DskipTests
FROM eclipse-temurin:17-jre
COPY --from=backend /app/target/planet-1.0.0.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
