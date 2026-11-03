# Imagem única: o backend serve também o Backoffice (deploy sem domínio próprio —
# ver docs/operations.md → "Deploy"). Contexto de build: esta pasta (management/).

# 1) Backoffice — só `vite build`: o `tsc -b` do script "build" tem erros antigos
#    por resolver e falharia a imagem (ver docs/commands.md).
FROM node:22-alpine AS frontend
WORKDIR /app
COPY managementfrontend/apps/backoffice/package.json managementfrontend/apps/backoffice/package-lock.json ./
RUN npm ci
COPY managementfrontend/apps/backoffice/ ./
# Vem do workflow de deploy (--build-arg); vazio, o seletor de mapa só avisa que falta.
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
# Afinado para a máquina de 1 GB do Fly.io, a preferir memória a velocidade (ver
# docs/operations.md → "Memória"; com a heap a 60%, a app parada já ocupava ~870 MB e
# uma leitura de QR pesada fazia o kernel matar o processo):
# - heap a 40% (~400 MB): o arranque não precisa de mais, só usa o que lhe derem; o resto
#   fica para metaspace (~110 MB), threads e a memória nativa do OpenCV, fora da heap;
# - MinHeapFreeRatio/MaxHeapFreeRatio: a heap devolve a memória em vez de ficar inchada;
# - TieredStopAtLevel=1: só o compilador C1. A app fica mais lenta em CPU, mas sem o C2 e
#   a sua code cache o processo ocupa bem menos;
# - SerialGC: o de menor overhead, chega para uma app interna de poucos utilizadores;
# - -Xss512k: cerca de 26 threads, com metade da pilha;
# - tetos ao metaspace e à code cache, para a memória fora da heap não crescer sem limite;
# - ExitOnOutOfMemoryError: sem memória, o processo sai e o Fly reinicia a máquina, em vez
#   de ficar meio-vivo a responder erros;
# - MALLOC_ARENA_MAX: a glibc cria áreas de memória por thread, e o OpenCV aloca por lá.
# Substituível por um secret/env JAVA_TOOL_OPTIONS no Fly (ex. ao mudar o tamanho da máquina).
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=40 -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=25 -XX:TieredStopAtLevel=1 -Xss512k -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError"
ENV MALLOC_ARENA_MAX=2
ENTRYPOINT ["java", "-jar", "app.jar"]
