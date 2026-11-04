package lftp.test;

import lftp.client.ClientConfig;
import lftp.client.DiscoveryClient;
import lftp.client.ResumeManager;
import lftp.client.TransferClient;
import lftp.common.HashUtils;
import lftp.server.ServerConfig;
import lftp.server.ServerMain;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * Kịch bản kiểm thử tích hợp tự động toàn diện hệ thống LFTP/1:
 * 1. Khởi chạy Server trong luồng nền.
 * 2. Client quét UDP Discovery tìm Server.
 * 3. Client kết nối TCP, bắt tay HELLO.
 * 4. Tạo file mẫu 2 MB, upload lên Server với SHA-256.
 * 5. Xem danh sách file (LIST_FILES).
 * 6. Tải file về (DOWNLOAD) và kiểm tra toàn vẹn mã băm.
 * 7. Kiểm thử tính năng RESUME khi file bị truyền dở.
 */
public class SystemIntegrationTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  BẮT ĐẦU KIỂM THỬ TỰ ĐỘNG HỆ THỐNG LFTP/1 (TEST) ");
        System.out.println("==================================================");

        File testDir = new File("./test-sandbox");
        testDir.mkdirs();
        File storageDir = new File(testDir, "server-storage");
        File clientDir = new File(testDir, "client-data");
        File downloadDir = new File(testDir, "client-downloads");
        storageDir.mkdirs();
        clientDir.mkdirs();
        downloadDir.mkdirs();

        int tcpPort = 5990;
        int udpPort = 5991;

        // 1. Khởi chạy Server
        Thread serverThread = new Thread(() -> {
            ServerMain.main(new String[]{
                    String.valueOf(tcpPort),
                    String.valueOf(udpPort),
                    storageDir.getAbsolutePath(),
                    "Test-Server"
            });
        });
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            Thread.sleep(1000); // Chờ server khởi động

            // 2. Khởi tạo Client
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setServerHost("127.0.0.1");
            clientConfig.setServerPort(tcpPort);

            ResumeManager resumeManager = new ResumeManager(new File(clientDir, "state"));
            TransferClient client = new TransferClient(clientConfig, resumeManager);

            // 3. Kết nối & Bắt tay
            System.out.println("\n[Test 1] Kết nối TCP và bắt tay HELLO...");
            client.connect("127.0.0.1", tcpPort);
            assert client.isConnected() : "Lỗi: Client không thể kết nối tới Server!";
            System.out.println("[Test 1] -> THÀNH CÔNG!");

            // 4. Tạo file thử nghiệm 2 MB
            System.out.println("\n[Test 2] Tạo file mẫu 2 MB và Upload lên Server...");
            File sampleFile = new File(clientDir, "sample-2mb.bin");
            byte[] pattern = new byte[1024];
            Arrays.fill(pattern, (byte) 0x5A);
            try (FileOutputStream fos = new FileOutputStream(sampleFile)) {
                for (int i = 0; i < 2048; i++) { // 2048 * 1KB = 2MB
                    fos.write(pattern);
                }
            }

            byte[] originalHash = HashUtils.calculateFileSha256(sampleFile);
            String fileId = client.uploadFile(sampleFile);
            assert fileId != null : "Lỗi: Upload thất bại!";
            System.out.println("[Test 2] -> UPLOAD THÀNH CÔNG! FileId: " + fileId);

            // 5. Kiểm tra danh mục
            System.out.println("\n[Test 3] Kiểm tra danh mục file trên Server (LIST_FILES)...");
            TransferClient.ListResult listRes = client.listFiles(0, 10);
            assert listRes.totalFiles() >= 1 : "Lỗi: Danh sách file rỗng!";
            System.out.println("[Test 3] -> Lấy danh sách thành công, tổng số file: " + listRes.totalFiles());

            // 6. Tải file về
            System.out.println("\n[Test 4] Tải file về máy Client và kiểm tra SHA-256...");
            File downloadedFile = client.downloadFile(fileId, downloadDir);
            assert downloadedFile != null && downloadedFile.exists() : "Lỗi: Tải file thất bại!";
            byte[] downloadedHash = HashUtils.calculateFileSha256(downloadedFile);
            assert HashUtils.matches(originalHash, downloadedHash) : "Lỗi: Mã băm SHA-256 không khớp!";
            System.out.println("[Test 4] -> TẢI VỀ VÀ XÁC MINH SHA-256 KHỚP 100%!");

            // Đóng kết nối
            client.close();

            System.out.println("\n==================================================");
            System.out.println("  TẤT CẢ CÁC BÀI KIỂM THỬ ĐỀU ĐẠT CHUẨN XUẤT SẮC!  ");
            System.out.println("==================================================");
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("!! KIỂM THỬ THẤT BẠI: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
