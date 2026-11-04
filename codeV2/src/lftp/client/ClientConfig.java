package lftp.client;

import lftp.common.Protocol;

import java.io.File;
import java.net.InetAddress;

/**
 * Cấu hình cho LFTP Client.
 */
public class ClientConfig {

    private final String clientName;
    private String serverHost;
    private int serverPort;
    private final File clientStateDir;
    private final File downloadDir;

    public ClientConfig() {
        String hostName = "client";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        this.clientName = hostName;
        this.serverHost = null; // Sẽ được tìm qua UDP Broadcast hoặc người dùng nhập
        this.serverPort = Protocol.DEFAULT_TCP_PORT;
        this.clientStateDir = new File("./client-state");
        this.downloadDir = new File("./downloads");

        if (!this.clientStateDir.exists()) {
            this.clientStateDir.mkdirs();
        }
        if (!this.downloadDir.exists()) {
            this.downloadDir.mkdirs();
        }
    }

    public String getClientName() {
        return clientName;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public File getClientStateDir() {
        return clientStateDir;
    }

    public File getDownloadDir() {
        return downloadDir;
    }
}

