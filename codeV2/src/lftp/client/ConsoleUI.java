package lftp.client;

import lftp.common.FileInfo;
import lftp.common.HashUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Giao diện dòng lệnh tương tác trên luồng chính (Main Thread - Luồng 1).
 * Cho phép người dùng nhập lệnh, xem danh mục phân trang và can thiệp (pause/resume/cancel)
 * mà không bao giờ bị đơ giao diện.
 */
public class ConsoleUI {

    private final ClientConfig config;
    private final TransferClient client;
    private final ResumeManager resumeManager;
    private final ExecutorService transferExecutor = Executors.newSingleThreadExecutor();
    private Future<?> activeTransferTask;
    private volatile boolean isRunning = true;

    public ConsoleUI(ClientConfig config, TransferClient client, ResumeManager resumeManager) {
        this.config = config;
        this.client = client;
        this.resumeManager = resumeManager;
    }

    public void start() {
        printBanner();
        Scanner scanner = new Scanner(System.in);

        // Tự động tìm kiếm server qua UDP Broadcast khi vừa bật client
        autoDiscoverAndConnect();

        while (isRunning) {
            System.out.print("\nLFTP> ");
            if (!scanner.hasNextLine()) break;

            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "discover" -> autoDiscoverAndConnect();
                    case "connect" -> handleConnect(parts);
                    case "list", "ls" -> handleList(parts);
                    case "upload", "up" -> handleUpload(line);
                    case "download", "down" -> handleDownload(line);
                    case "pause" -> handlePause();
                    case "resume" -> handleResume();
                    case "cancel" -> handleCancel();
                    case "pending" -> handlePending();
                    case "status" -> handleStatus();
                    case "help", "?" -> printHelp();
                    case "exit", "quit" -> handleExit();
                    default -> System.out.println("Lệnh không hợp lệ. Gõ 'help' để xem danh sách lệnh.");
                }
            } catch (Exception e) {
                System.out.println("!! LỖI: " + e.getMessage());
            }
        }
        transferExecutor.shutdownNow();
    }

    private void autoDiscoverAndConnect() {
        System.out.println("[Client] Đang tự động quét tìm Server trong mạng LAN...");
        DiscoveryClient.ServerOffer offer = DiscoveryClient.discoverServer(2500);
        if (offer != null) {
            try {
                client.connect(offer.ipAddress(), offer.tcpPort());
            } catch (Exception e) {
                System.out.println("[Client] Không thể kết nối tới server tìm được: " + e.getMessage());
            }
        } else {
            System.out.println("[Client] Chưa tìm thấy server tự động. Bạn có thể dùng lệnh: connect <ip> [port]");
        }
    }

    private void handleConnect(String[] parts) throws Exception {
        if (parts.length < 2) {
            System.out.println("Cú pháp: connect <ip_server> [port]");
            return;
        }
        String host = parts[1];
        int port = (parts.length >= 3) ? Integer.parseInt(parts[2]) : config.getServerPort();
        client.connect(host, port);
    }

    private void handleList(String[] parts) throws Exception {
        int page = 0;
        int size = 10;
        if (parts.length >= 2) page = Integer.parseInt(parts[1]);
        if (parts.length >= 3) size = Integer.parseInt(parts[2]);

        TransferClient.ListResult res = client.listFiles(page, size);
        System.out.printf("%n=== DANH MỤC TỆP TRÊN MÁY CHỦ (Trang %d, Tổng cộng: %d tệp) ===%n",
                res.pageIndex(), res.totalFiles());
        if (res.entries().isEmpty()) {
            System.out.println("(Không có tệp nào trong trang này)");
            if (page > 0) {
                System.out.println("Lưu ý: Chỉ số trang bắt đầu từ 0. Hãy gõ 'list 0' hoặc 'list' để xem trang đầu tiên.");
            }
            return;
        }

        System.out.printf("%-18s | %-24s | %-10s | %-16s | %-16s%n",
                "Mã tệp (FileId)", "Tên hiển thị", "Kích thước", "Mã SHA-256 (rút gọn)", "Thời điểm tạo");
        System.out.println("-".repeat(95));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (FileInfo fi : res.entries()) {
            String hashShort = fi.getFileHashHex().substring(0, 12) + "...";
            String dateStr = sdf.format(new Date(fi.getCreatedAtEpochMillis()));
            System.out.printf("%-18s | %-24s | %-10s | %-16s | %-16s%n",
                    fi.getFileId(),
                    truncate(fi.getDisplayName(), 24),
                    TransferProgress.formatBytes(fi.getFileSize()),
                    hashShort,
                    dateStr);
        }
        System.out.println("-".repeat(95));
        System.out.println("Gõ: 'list <page>' để xem trang tiếp theo.");
    }

    private void handleUpload(String fullLine) {
        String filePath = fullLine.replaceFirst("(?i)^(upload|up)\\s*", "").trim();
        filePath = cleanPath(filePath);

        if (filePath.isEmpty()) {
            System.out.println("Cú pháp: upload <đường_dẫn_tệp_cục_bộ>");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Tệp không tồn tại: " + file.getAbsolutePath());
            return;
        }

        if (activeTransferTask != null && !activeTransferTask.isDone()) {
            System.out.println("Đang có một lượt truyền khác đang chạy! Hãy đợi hoặc gõ 'cancel'.");
            return;
        }

        activeTransferTask = transferExecutor.submit(() -> {
            try {
                client.uploadFile(file);
            } catch (Exception e) {
                System.out.println("\n!! LỖI TRUYỀN TẢI: " + e.getMessage());
            }
        });
    }

    private void handleDownload(String fullLine) {
        String args = fullLine.replaceFirst("(?i)^(download|down)\\s*", "").trim();
        if (args.isEmpty()) {
            System.out.println("Cú pháp: download <fileId> [thư_mục_lưu]");
            return;
        }

        String fileId;
        String targetPath = null;

        if (args.startsWith("\"")) {
            int nextQuote = args.indexOf('"', 1);
            if (nextQuote != -1) {
                fileId = args.substring(1, nextQuote).trim();
                targetPath = cleanPath(args.substring(nextQuote + 1));
            } else {
                fileId = cleanPath(args);
            }
        } else {
            int spaceIdx = args.indexOf(' ');
            if (spaceIdx != -1) {
                fileId = args.substring(0, spaceIdx).trim();
                targetPath = cleanPath(args.substring(spaceIdx + 1));
            } else {
                fileId = cleanPath(args);
            }
        }

        File targetDir = (targetPath != null && !targetPath.isEmpty()) ? new File(targetPath) : config.getDownloadDir();

        if (activeTransferTask != null && !activeTransferTask.isDone()) {
            System.out.println("Đang có một lượt truyền khác đang chạy!");
            return;
        }

        activeTransferTask = transferExecutor.submit(() -> {
            try {
                client.downloadFile(fileId, targetDir);
            } catch (Exception e) {
                System.out.println("\n!! LỖI TẢI VỀ: " + e.getMessage());
            }
        });
    }

    private static String cleanPath(String p) {
        if (p == null) return "";
        p = p.trim();
        // Bỏ dấu nháy kép "..." hoặc nháy đơn '...' do Windows "Copy as path" thêm vào
        while ((p.startsWith("\"") && p.endsWith("\"")) || (p.startsWith("'") && p.endsWith("'"))) {
            if (p.length() >= 2) {
                p = p.substring(1, p.length() - 1).trim();
            } else {
                break;
            }
        }
        return p;
    }

    private void handlePause() {
        if (activeTransferTask != null && !activeTransferTask.isDone()) {
            client.pauseTransfer();
            System.out.println("[Client] Đã gửi lệnh TẠM DỪNG (PAUSE) lượt truyền.");
        } else {
            System.out.println("Không có lượt truyền tải nào đang chạy.");
        }
    }

    private void handleResume() {
        if (activeTransferTask != null && !activeTransferTask.isDone()) {
            client.resumeTransfer();
            System.out.println("[Client] Đã gửi lệnh TIẾP TỤC (RESUME) lượt truyền.");
        } else {
            System.out.println("Không có lượt truyền tải nào đang bị tạm dừng.");
        }
    }

    private void handleCancel() {
        if (activeTransferTask != null && !activeTransferTask.isDone()) {
            client.cancelTransfer();
            System.out.println("[Client] Đã gửi lệnh HỦY (CANCEL) lượt truyền.");
        } else {
            System.out.println("Không có lượt truyền tải nào để hủy.");
        }
    }

    private void handlePending() {
        List<ResumeManager.UploadState> pending = resumeManager.listPendingUploads();
        System.out.printf("%n=== CÁC PHIÊN TẢI LÊN DỞ ĐÃ LƯU (%d phiên) ===%n", pending.size());
        if (pending.isEmpty()) {
            System.out.println("(Không có phiên dở nào)");
            return;
        }
        for (ResumeManager.UploadState s : pending) {
            System.out.printf("- File: %s | UploadID: %s | Đã truyền: %s / %s%n",
                    s.localFilePath(), s.uploadId(),
                    TransferProgress.formatBytes(s.lastOffset()),
                    TransferProgress.formatBytes(s.fileSize()));
        }
        System.out.println("Gợi ý: Gõ lại lệnh 'upload <đường_dẫn_file>' để tiếp tục tải lên từ vị trí đã dừng.");
    }

    private void handleStatus() {
        System.out.println("----------------------------------------");
        System.out.println("Trạng thái kết nối : " + (client.isConnected() ? "ĐÃ KẾT NỐI" : "CHƯA KẾT NỐI"));
        if (client.isConnected()) {
            System.out.println("Máy chủ            : " + client.getServerName() + " (" + config.getServerHost() + ":" + config.getServerPort() + ")");
        }
        TransferProgress p = client.getActiveProgress();
        if (activeTransferTask != null && !activeTransferTask.isDone() && p != null) {
            System.out.printf("Tiến trình hiện tại: %.1f%% | Tốc độ: %.2f MB/s%n", p.getPercent(), p.getSpeedMBs());
        }
        System.out.println("----------------------------------------");
    }

    private void handleExit() {
        System.out.println("Đang ngắt kết nối và thoát...");
        client.close();
        isRunning = false;
    }

    private void printBanner() {
        System.out.println("===============================================================");
        System.out.println("  LFTP/1 CLIENT - ỨNG DỤNG TRUYỀN TỆP MẠNG NỘI BỘ (LAN) CLI    ");
        System.out.println("  Nhóm TKD: Nguyễn Trung Tường - Bùi Văn Đạt - Nguyễn Đình Khải ");
        System.out.println("===============================================================");
        System.out.println("Gõ 'help' để xem danh sách các lệnh hỗ trợ.");
    }

    private void printHelp() {
        System.out.println("\nDANH SÁCH LỆNH:");
        System.out.println("  discover                     : Tự động tìm máy chủ trong mạng LAN qua UDP Broadcast");
        System.out.println("  connect <ip> [port]          : Kết nối thủ công tới máy chủ (mặc định cổng 5000)");
        System.out.println("  list [page] [pageSize]       : Xem danh mục tệp hoàn tất trên server theo trang");
        System.out.println("  upload <path>                : Tải lên tệp (hỗ trợ tự động Resume nếu đứt mạng)");
        System.out.println("  download <fileId> [saveDir]  : Tải tệp từ server về thư mục lưu (mặc định ./downloads)");
        System.out.println("  pause                        : Tạm dừng lượt truyền tải hiện tại");
        System.out.println("  resume                       : Tiếp tục lượt truyền tải vừa tạm dừng");
        System.out.println("  cancel                       : Hủy bỏ lượt truyền tải hiện tại");
        System.out.println("  pending                      : Xem danh sách các tệp đang tải dở");
        System.out.println("  status                       : Kiểm tra trạng thái mạng và tiến trình");
        System.out.println("  help                         : In bảng hướng dẫn này");
        System.out.println("  exit / quit                  : Đóng ứng dụng");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}

