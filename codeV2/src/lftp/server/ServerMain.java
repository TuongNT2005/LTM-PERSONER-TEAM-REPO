package lftp.server;

import java.io.IOException;

/**
 * Điểm khởi chạy chính của ứng dụng LFTP Server.
 * Khởi tạo các dịch vụ cốt lõi, dọn dẹp file tạm mồ côi và mở các cổng mạng.
 */
public class ServerMain {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  LFTP/1 SERVER - HỆ THỐNG TRUYỀN TỆP TIN CẬY MẠNG NỘI BỘ (LAN) ");
        System.out.println("  Nhóm TKD: Nguyễn Trung Tường - Bùi Văn Đạt - Nguyễn Đình Khải ");
        System.out.println("===============================================================");

        // Đọc cấu hình từ đối số dòng lệnh hoặc biến môi trường
        int tcpPort = 0;
        int udpPort = 0;
        String storageDir = null;
        String serverName = null;

        if (args.length >= 1) tcpPort = Integer.parseInt(args[0]);
        if (args.length >= 2) udpPort = Integer.parseInt(args[1]);
        if (args.length >= 3) storageDir = args[2];
        if (args.length >= 4) serverName = args[3];

        ServerConfig config = new ServerConfig(serverName, tcpPort, udpPort, storageDir);

        System.out.println("[Config] Tên Server      : " + config.getServerName());
        System.out.println("[Config] Cổng TCP Data   : " + config.getTcpPort());
        System.out.println("[Config] Cổng UDP Tìm kiếm: " + config.getUdpPort());
        System.out.println("[Config] Thư mục lưu trữ : " + config.getStorageDir().getAbsolutePath());
        System.out.println("[Config] Hạn ngạch Quota : " + (config.getStorageQuotaBytes() / (1024 * 1024 * 1024)) + " GiB");
        System.out.println("[Config] Tối đa Clients  : " + config.getMaxClients() + " kết nối");
        System.out.println("---------------------------------------------------------------");

        // 1. Khởi tạo tầng lưu trữ vật lý
        FileStorage fileStorage = new FileStorage(config.getStorageDir());

        // 2. Dọn sạch file .tmp mồ côi trước khi mở mạng
        fileStorage.cleanupTempFilesOnStartup();

        // 3. Khởi tạo người quản lý file và quota
        FileManager fileManager = new FileManager(config.getStorageDir(), config.getStorageQuotaBytes());

        // 4. Khởi tạo người quản lý phiên truyền tải
        SessionManager sessionManager = new SessionManager(fileManager, fileStorage);

        // 5. Khởi tạo bộ lắng nghe kết nối TCP với Thread Pool
        ConnectionListener connectionListener = new ConnectionListener(
                config, fileManager, fileStorage, sessionManager);

        // 6. Khởi tạo bộ phản hồi tìm kiếm UDP Broadcast
        DiscoveryServer discoveryServer = new DiscoveryServer(config);

        // 7. Khởi tạo luồng quét timeout định kỳ
        ConnectionWatcher connectionWatcher = new ConnectionWatcher(connectionListener, sessionManager);
        Thread watcherThread = new Thread(connectionWatcher, "lftp-conn-watcher");
        watcherThread.setDaemon(true);

        try {
            // Mở cổng TCP
            connectionListener.start();
            // Mở cổng UDP
            discoveryServer.start();
            // Kích hoạt luồng quét dọn
            watcherThread.start();

            System.out.println(">> Server đã khởi động thành công và sẵn sàng phục vụ!");
        } catch (IOException e) {
            System.err.println("!! LỖI KHỞI ĐỘNG SERVER: " + e.getMessage());
            System.exit(1);
        }

        // Đăng ký Shutdown Hook để đóng an toàn khi bấm Ctrl+C hoặc tắt tiến trình
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Đang tắt an toàn máy chủ...");
            connectionWatcher.stop();
            discoveryServer.stop();
            connectionListener.stop();
            System.out.println("[Server] Đã giải phóng toàn bộ tài nguyên. Tạm biệt!");
        }));
    }
}

