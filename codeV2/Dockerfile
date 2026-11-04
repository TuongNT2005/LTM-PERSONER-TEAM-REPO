# Multi-stage Dockerfile cho LFTP/1 Server
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build
COPY src ./src

# Biên dịch toàn bộ mã nguồn Java
RUN find src -name "*.java" > sources.txt && \
    mkdir -p bin && \
    javac -encoding UTF-8 -d bin @sources.txt

# Giai đoạn runtime siêu nhẹ
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /build/bin ./bin

# Khởi tạo thư mục dữ liệu bền vững
RUN mkdir -p /data
VOLUME /data

# Mở các cổng mạng
# Cổng TCP 5000: Truyền nhận file và điều khiển
# Cổng UDP 5001: Tìm kiếm máy chủ LAN Broadcast
EXPOSE 5000/tcp
EXPOSE 5001/udp

ENV LFTP_STORAGE_DIR=/data
ENV LFTP_SERVER_NAME="TKD-Docker-Server"

# Khởi chạy server
ENTRYPOINT ["java", "-cp", "/app/bin", "lftp.server.ServerMain"]

