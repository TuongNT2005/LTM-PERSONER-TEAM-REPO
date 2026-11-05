package lftp.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Tầng thao tác vật lý với hệ thống tệp đĩa cứng (Disk I/O).
 * Quản lý các file tạm (.tmp) và các file hoàn tất (.dat).
 */
public class FileStorage {

    private final File storageDir;

    public FileStorage(File storageDir) {
        this.storageDir = storageDir;
        if (!this.storageDir.exists()) {
            boolean created = this.storageDir.mkdirs();
            if (!created && !this.storageDir.exists()) {
                throw new IllegalStateException("Không thể tạo thư mục lưu trữ: " + storageDir.getAbsolutePath());
            }
        }
    }

    /**
     * Quét và xóa sạch tất cả file .tmp mồ côi khi server khởi động.
     * Giải phóng dung lượng đĩa do lần tắt máy đột ngột trước đó để lại.
     */
    public int cleanupTempFilesOnStartup() {
        File[] tempFiles = storageDir.listFiles((dir, name) -> name.endsWith(".tmp"));
        int cleanedCount = 0;
        long cleanedBytes = 0;

        if (tempFiles != null) {
            for (File f : tempFiles) {
                long len = f.length();
                if (f.delete()) {
                    cleanedCount++;
                    cleanedBytes += len;
                }
            }
        }
        if (cleanedCount > 0) {
            System.out.printf("[FileStorage] Đã dọn dẹp %d file tạm mồ côi lúc khởi động (Giải phóng %.2f MB)%n",
                    cleanedCount, cleanedBytes / (1024.0 * 1024.0));
        }
        return cleanedCount;
    }

    public File getTempFile(String uploadId) {
        return new File(storageDir, uploadId + ".tmp");
    }

    public RandomAccessFile openTempFile(String uploadId) throws FileNotFoundException {
        File f = getTempFile(uploadId);
        return new RandomAccessFile(f, "rw");
    }

    public long getTempFileSize(String uploadId) {
        File f = getTempFile(uploadId);
        return f.exists() ? f.length() : 0L;
    }

    public boolean deleteTempFile(String uploadId) {
        File f = getTempFile(uploadId);
        if (f.exists()) {
            return f.delete();
        }
        return false;
    }

    public File getCompletedFile(String fileId) {
        return new File(storageDir, fileId + ".dat");
    }

    public RandomAccessFile openForDownload(String fileId) throws FileNotFoundException {
        File f = getCompletedFile(fileId);
        if (!f.exists()) {
            throw new FileNotFoundException("File không tồn tại trên đĩa máy chủ: " + fileId);
        }
        return new RandomAccessFile(f, "r");
    }

    /**
     * Đổi tên nguyên tử file tạm thành file chính thức khi xác nhận COMMIT thành công.
     */
    public void saveCompletedFile(String uploadId, String fileId) throws IOException {
        File temp = getTempFile(uploadId);
        File completed = getCompletedFile(fileId);
        if (!temp.exists()) {
            throw new FileNotFoundException("File tạm không tồn tại: " + temp.getAbsolutePath());
        }
        Files.move(temp.toPath(), completed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}

