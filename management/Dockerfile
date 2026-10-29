# Imagem única: o backend serve também o Backoffice (deploy sem domínio próprio —
# ver docs/operations.md → "Deploy"). Contexto de build: esta pasta (management/).

# 1) Backoffice — só `vite build`: o `tsc -b` do script "build" tem erros antigos
#    por resolver e falharia a imagem (ver docs/commands.md).
FROM node:22-alpine AS frontend
WORKDIR /app
COPY managementfrontend/apps/backoffice/package.json managementfrontend/apps/backoffice/package-lock.json ./
RUN npm ci
COPY managementfrontend/apps/backoffice/ ./
# O Render passa as variáveis de ambiente do serviço como build args.
ARG VITE_GOOGLE_MAPS_API_KEY
# Vazio = mesma origem: o Axios fala com o próprio host que serviu a página.
ENV VITE_API_URL=""
RUN npx vite build

# 2) Backend, com o build do Backoffice nos estáticos
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY managementapi/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY managementapi/src ./src
COPY --from=frontend /app/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3) Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
