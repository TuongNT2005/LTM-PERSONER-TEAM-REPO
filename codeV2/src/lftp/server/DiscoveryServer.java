package lftp.server;

import lftp.common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * Dịch vụ lắng nghe gói tin tìm kiếm qua UDP Broadcast trên cổng 5001.
 * Khi nhận "LFTP_DISCOVER", tự động phản hồi Unicast tên server và cổng TCP.
 */
public class DiscoveryServer implements Runnable {

    private final ServerConfig config;
    private DatagramSocket socket;
    private volatile boolean running = true;

    public DiscoveryServer(ServerConfig config) {
        this.config = config;
    }

    public void start() {
        Thread thread = new Thread(this, "lftp-discovery-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        this.running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(config.getUdpPort());
            System.out.printf("[DiscoveryServer] Đang lắng nghe UDP Broadcast trên cổng %d...%n", config.getUdpPort());

            byte[] buffer = new byte[1024];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (msg.startsWith(Protocol.DISCOVERY_REQUEST)) {
                    // Phản hồi Unicast thẳng về IP và cổng của client
                    String response = String.format("%s %s %d",
                            Protocol.DISCOVERY_OFFER_PREFIX, config.getServerName(), config.getTcpPort());
                    byte[] respData = response.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket respPacket = new DatagramPacket(
                            respData, respData.length, packet.getAddress(), packet.getPort());
                    socket.send(respPacket);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[DiscoveryServer] Lỗi UDP Discovery: " + e.getMessage());
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}

