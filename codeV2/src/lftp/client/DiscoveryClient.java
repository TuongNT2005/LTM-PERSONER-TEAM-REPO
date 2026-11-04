package lftp.client;

import lftp.common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Tự động tìm kiếm địa chỉ IP và cổng của LFTP Server trong mạng LAN
 * bằng cơ chế gửi gói tin UDP Broadcast tới 255.255.255.255:5001.
 */
public class DiscoveryClient {

    public record ServerOffer(String serverName, String ipAddress, int tcpPort) {}

    public static ServerOffer discoverServer(int timeoutMs) {
        System.out.println("[Discovery] Đang phát gói tin UDP Broadcast tìm kiếm server trong mạng LAN...");
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(timeoutMs > 0 ? timeoutMs : 3000);

            // 1. Gửi gói tin broadcast tới toàn mạng nội bộ
            byte[] reqData = Protocol.DISCOVERY_REQUEST.getBytes(StandardCharsets.UTF_8);
            DatagramPacket reqPacket = new DatagramPacket(
                    reqData, reqData.length,
                    InetAddress.getByName("255.255.255.255"), Protocol.DEFAULT_UDP_PORT);
            socket.send(reqPacket);

            // 2. Chờ nhận phản hồi offer từ máy chủ
            byte[] buf = new byte[1024];
            DatagramPacket respPacket = new DatagramPacket(buf, buf.length);
            socket.receive(respPacket);

            String reply = new String(respPacket.getData(), 0, respPacket.getLength(), StandardCharsets.UTF_8).trim();
            if (reply.startsWith(Protocol.DISCOVERY_OFFER_PREFIX)) {
                String[] parts = reply.split(" ");
                if (parts.length >= 3) {
                    String serverName = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String ip = respPacket.getAddress().getHostAddress();
                    System.out.printf("[Discovery] Đã tìm thấy Server: %s tại IP: %s (Cổng TCP: %d)%n",
                            serverName, ip, port);
                    return new ServerOffer(serverName, ip, port);
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[Discovery] Quá hạn thời gian tìm kiếm (không nhận được phản hồi từ server nào).");
        } catch (IOException e) {
            System.err.println("[Discovery] Lỗi gửi/nhận UDP broadcast: " + e.getMessage());
        }
        return null;
    }
}

