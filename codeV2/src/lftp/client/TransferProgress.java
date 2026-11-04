package lftp.client;

/**
 * Theo dõi tiến độ truyền file, tính toán tốc độ truyền tải trung bình (MB/s)
 * và ước lượng thời gian còn lại (ETA).
 */
public class TransferProgress {

    private final String fileName;
    private final long totalBytes;
    private long transferredBytes;

    private final long startTimeMs;
    private long lastSampleTimeMs;
    private long lastSampleBytes;
    private double currentSpeedMbps;

    public TransferProgress(String fileName, long totalBytes, long initialOffset) {
        this.fileName = fileName;
        this.totalBytes = totalBytes;
        this.transferredBytes = initialOffset;
        this.startTimeMs = System.currentTimeMillis();
        this.lastSampleTimeMs = this.startTimeMs;
        this.lastSampleBytes = initialOffset;
        this.currentSpeedMbps = 0.0;
    }

    public synchronized void update(long currentBytes) {
        this.transferredBytes = currentBytes;
        long now = System.currentTimeMillis();
        long timeDelta = now - lastSampleTimeMs;

        // Cập nhật tốc độ mỗi 300ms một lần để số liệu mượt mà
        if (timeDelta >= 300) {
            long bytesDelta = currentBytes - lastSampleBytes;
            double speedBytesPerSec = (bytesDelta * 1000.0) / timeDelta;
            this.currentSpeedMbps = speedBytesPerSec / (1024.0 * 1024.0); // Đổi ra MB/s

            this.lastSampleTimeMs = now;
            this.lastSampleBytes = currentBytes;
        }
    }

    public double getPercent() {
        if (totalBytes <= 0) return 100.0;
        return (transferredBytes * 100.0) / totalBytes;
    }

    public double getSpeedMBs() {
        return currentSpeedMbps;
    }

    public long getEtaSeconds() {
        if (currentSpeedMbps <= 0.01) return -1;
        long remainingBytes = totalBytes - transferredBytes;
        double speedBytesPerSec = currentSpeedMbps * 1024.0 * 1024.0;
        return (long) (remainingBytes / speedBytesPerSec);
    }

    /**
     * Tạo chuỗi thanh tiến độ trực quan hiển thị ra màn hình terminal.
     */
    public synchronized String renderProgressBar(int barWidth) {
        double pct = getPercent();
        int filled = (int) ((pct / 100.0) * barWidth);
        if (filled > barWidth) filled = barWidth;

        StringBuilder sb = new StringBuilder();
        sb.append("\r[");
        for (int i = 0; i < filled; i++) {
            sb.append("=");
        }
        if (filled < barWidth) {
            sb.append(">");
            for (int i = filled + 1; i < barWidth; i++) {
                sb.append(" ");
            }
        }
        sb.append(String.format("] %5.1f%% | %6.2f MB/s", pct, currentSpeedMbps));

        long eta = getEtaSeconds();
        if (eta >= 0) {
            sb.append(String.format(" | ETA: %2ds", eta));
        } else {
            sb.append(" | ETA: --s");
        }

        sb.append(String.format(" (%s / %s)",
                formatBytes(transferredBytes), formatBytes(totalBytes)));

        return sb.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.2f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}

