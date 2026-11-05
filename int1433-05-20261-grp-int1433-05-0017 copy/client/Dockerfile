# ============================================================
# Client Dockerfile — Network Programming Assignment (INT1433)
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY src/ ./src/
RUN javac -encoding UTF-8 -d bin $(find src -name "*.java")

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/bin ./bin

# Tạo sẵn thư mục lưu trạng thái và download
RUN mkdir -p ./client-state ./downloads

CMD ["java", "-cp", "bin", "Client"]
