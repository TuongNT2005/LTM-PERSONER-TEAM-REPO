package lftp.server;

import lftp.common.Protocol;

import java.io.File;

/**
 * Cấu hình tham số hoạt động cho LFTP Server.
 * Hỗ trợ nạp từ biến môi trường (phục vụ chạy Docker) hoặc tham số dòng lệnh.
 */
public class ServerConfig {

    private final String serverName;
    private final int tcpPort;
    private final int udpPort;
    private final File storageDir;
    private final int maxClients;
    private final int chunkSize;
    private final long maxFileBytes;
    private final long storageQuotaBytes;

    public ServerConfig(String serverName, int tcpPort, int udpPort, String storageDirPath) {
        if (serverName == null || serverName.isBlank()) {
            serverName = System.getenv("SERVER_NAME");
            if (serverName == null) serverName = System.getenv("LFTP_SERVER_NAME");
        }
        this.serverName = (serverName != null && !serverName.isBlank()) ? serverName : "TKD-Server";

        if (tcpPort <= 0) {
            String p = System.getenv("SERVER_PORT");
            if (p == null) p = System.getenv("LFTP_TCP_PORT");
            if (p != null) {
                try { tcpPort = Integer.parseInt(p); } catch (NumberFormatException ignored) {}
            }
        }
        this.tcpPort = (tcpPort > 0) ? tcpPort : Protocol.DEFAULT_TCP_PORT;

        if (udpPort <= 0) {
            String up = System.getenv("UDP_PORT");
            if (up == null) up = System.getenv("LFTP_UDP_PORT");
            if (up != null) {
                try { udpPort = Integer.parseInt(up); } catch (NumberFormatException ignored) {}
            }
        }
        this.udpPort = (udpPort > 0) ? udpPort : Protocol.DEFAULT_UDP_PORT;
        
        if (storageDirPath == null || storageDirPath.isBlank()) {
            storageDirPath = System.getenv("STORAGE_DIR");
            if (storageDirPath == null) storageDirPath = System.getenv("LFTP_STORAGE_DIR");
        }
        String path = (storageDirPath != null && !storageDirPath.isBlank()) ? storageDirPath : "./storage";
        // Nếu trong Docker container có thư mục /data thì ưu tiên /data
        if (storageDirPath == null && new File("/data").isDirectory()) {
            path = "/data";
        }
        this.storageDir = new File(path);
        
        this.maxClients = Protocol.MAX_CLIENTS;
        this.chunkSize = Protocol.CHUNK_SIZE;
        this.maxFileBytes = Protocol.MAX_FILE_BYTES;
        this.storageQuotaBytes = Protocol.STORAGE_QUOTA_BYTES;
    }

    public static ServerConfig createDefault() {
        String sName = System.getenv("LFTP_SERVER_NAME");
        String portStr = System.getenv("LFTP_TCP_PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : Protocol.DEFAULT_TCP_PORT;
        String udpPortStr = System.getenv("LFTP_UDP_PORT");
        int uPort = (udpPortStr != null) ? Integer.parseInt(udpPortStr) : Protocol.DEFAULT_UDP_PORT;
        String dir = System.getenv("LFTP_STORAGE_DIR");

        return new ServerConfig(sName, port, uPort, dir);
    }

    public String getServerName() {
        return serverName;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public File getStorageDir() {
        return storageDir;
    }

    public int getMaxClients() {
        return maxClients;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }
}

