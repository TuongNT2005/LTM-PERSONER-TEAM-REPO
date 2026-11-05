# ============================================================
# Server Dockerfile — Network Programming Assignment (INT1433)
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY src/ ./src/
RUN javac -encoding UTF-8 -d bin $(find src -name "*.java")

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/bin ./bin

# Tạo sẵn thư mục lưu trữ dữ liệu
RUN mkdir -p /data ./storage

EXPOSE 5000
EXPOSE 5001/udp

CMD ["java", "-cp", "bin", "Server"]
