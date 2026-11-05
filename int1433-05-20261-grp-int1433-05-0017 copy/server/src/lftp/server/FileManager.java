package lftp.server;

import lftp.common.FileInfo;
import lftp.common.Protocol;
import lftp.common.RequestException;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quản lý danh mục tệp hoàn tất và kiểm soát hạn ngạch lưu trữ (Storage Quota).
 * Sử dụng biến nguyên tử AtomicLong với giải thuật CAS không khóa (Lock-free), triệt tiêu nguy cơ Deadlock.
 */
public class FileManager {

    private final long quotaBytes;
    private final AtomicLong usedBytes = new AtomicLong(0);
    private final AtomicLong reservedBytes = new AtomicLong(0);

    private final Map<String, FileInfo> catalog = new ConcurrentHashMap<>();
    private final File catalogIndexFile;

    public FileManager(File storageDir, long quotaBytes) {
        this.quotaBytes = quotaBytes;
        this.catalogIndexFile = new File(storageDir, "catalog.idx");
        loadCatalogFromDisk();
    }

    /**
     * Dành trước dung lượng khi bắt đầu Upload bằng giải thuật CAS không khóa.
     */
    public void reserveSpace(long bytes) throws RequestException {
        if (bytes <= 0) {
            return;
        }
        if (bytes > Protocol.MAX_FILE_BYTES) {
            throw new RequestException(Protocol.STATUS_TOO_LARGE,
                    "Dung lượng tệp (" + bytes + " bytes) vượt quá giới hạn " + 
                            (Protocol.MAX_FILE_BYTES / (1024L * 1024L * 1024L)) + " GiB cho phép!");
        }

        while (true) {
            long curReserved = reservedBytes.get();
            long curUsed = usedBytes.get();

            if (curUsed + curReserved + bytes > quotaBytes) {
                throw new RequestException(Protocol.STATUS_STORAGE_UNAVAILABLE,
                        String.format("Server hết dung lượng lưu trữ! (Đã dùng: %.2f MB, Đang chờ: %.2f MB, Cần thêm: %.2f MB, Hạn mức: %.2f MB)",
                                curUsed / 1048576.0, curReserved / 1048576.0, bytes / 1048576.0, quotaBytes / 1048576.0));
            }

            if (reservedBytes.compareAndSet(curReserved, curReserved + bytes)) {
                break; // Dành chỗ thành công tuyệt đối!
            }
        }
    }

    /**
     * Chuyển dung lượng từ reservedBytes sang usedBytes khi upload hoàn tất.
     */
    public void confirmSpace(long bytes) {
        reservedBytes.addAndGet(-bytes);
        usedBytes.addAndGet(bytes);
        saveCatalogToDisk();
    }

    /**
     * Hoàn trả dung lượng dành trước khi lượt upload thất bại hoặc hết hạn.
     */
    public void releaseReservedSpace(long bytes) {
        reservedBytes.addAndGet(-bytes);
    }

    /**
     * Thêm tệp đã hoàn tất vào danh mục.
     */
    public void addFile(FileInfo fileInfo) {
        catalog.put(fileInfo.getFileId(), fileInfo);
        saveCatalogToDisk();
    }

    public FileInfo findFile(String fileId) {
        return catalog.get(fileId);
    }

    public int getTotalFiles() {
        return catalog.size();
    }

    public long getUsedBytes() {
        return usedBytes.get();
    }

    public long getReservedBytes() {
        return reservedBytes.get();
    }

    /**
     * Lấy danh sách tệp theo cơ chế phân trang an toàn (Pagination).
     * Tạo bản sao danh sách (Snapshot) để sắp xếp và cắt trang mà không chặn luồng khác.
     */
    public List<FileInfo> listFiles(int pageIndex, int pageSize) {
        if (pageIndex < 0) pageIndex = 0;
        if (pageSize <= 0) pageSize = 50;
        if (pageSize > 100) pageSize = 100; // Giới hạn trang tối đa 100 file để gói tin nhỏ

        List<FileInfo> all = new ArrayList<>(catalog.values());
        // Sắp xếp file mới nhất lên đầu
        all.sort((a, b) -> Long.compare(b.getCreatedAtEpochMillis(), a.getCreatedAtEpochMillis()));

        int start = pageIndex * pageSize;
        if (start >= all.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(start + pageSize, all.size());
        return all.subList(start, end);
    }

    // --- LƯU TRỮ VÀ KHÔI PHỤC DANH MỤC TRÊN ĐĨA ---

    private synchronized void saveCatalogToDisk() {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(catalogIndexFile)))) {
            dos.writeInt(catalog.size());
            for (FileInfo fi : catalog.values()) {
                fi.writeToStream(dos);
            }
            dos.flush();
        } catch (IOException e) {
            System.err.println("[FileManager] Cảnh báo: Không thể lưu catalog.idx: " + e.getMessage());
        }
    }

    private synchronized void loadCatalogFromDisk() {
        if (!catalogIndexFile.exists()) {
            return;
        }
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(catalogIndexFile)))) {
            int count = dis.readInt();
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                FileInfo fi = FileInfo.readFromStream(dis);
                catalog.put(fi.getFileId(), fi);
                totalSize += fi.getFileSize();
            }
            usedBytes.set(totalSize);
            System.out.printf("[FileManager] Đã nạp %d file từ catalog.idx (Tổng dung lượng: %.2f MB)%n",
                    count, totalSize / 1048576.0);
        } catch (Exception e) {
            System.err.println("[FileManager] Cảnh báo: Lỗi đọc file catalog.idx cũ, tiến hành quét mới: " + e.getMessage());
        }
    }
}

