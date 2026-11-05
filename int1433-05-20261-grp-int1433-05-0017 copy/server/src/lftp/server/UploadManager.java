package lftp.server;

import lftp.common.FileInfo;
import lftp.common.HashUtils;
import lftp.common.Protocol;
import lftp.common.RequestException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

/**
 * Xử lý luồng tải lên tệp: ghi từng chunk dữ liệu vào file tạm trên đĩa
 * và đồng thời cập nhật mã băm SHA-256 lũy tiến vào bộ nhớ RAM.
 */
public class UploadManager implements AutoCloseable {

    private final TransferSession session;
    private final FileStorage fileStorage;
    private final FileManager fileManager;
    private RandomAccessFile raf;

    public UploadManager(TransferSession session, FileStorage fileStorage, FileManager fileManager)
            throws IOException {
        this.session = session;
        this.fileStorage = fileStorage;
        this.fileManager = fileManager;
        this.raf = fileStorage.openTempFile(session.getSessionId());
    }

    /**
     * Ghi một chunk dữ liệu và cập nhật băm SHA-256 lũy tiến.
     */
    public synchronized long writeChunk(long offset, byte[] data, int length)
            throws RequestException, IOException {
        session.lock();
        try {
            long expectedOffset = session.getCurrentOffset();
            if (offset != expectedOffset) {
                throw new RequestException(Protocol.STATUS_CONFLICT,
                        "Lệch vị trí offset! Server đang chờ từ byte: " + expectedOffset, expectedOffset);
            }

            if (offset + length > session.getFileSize()) {
                throw new RequestException(Protocol.STATUS_BAD_REQUEST,
                        "Dữ liệu gửi vượt quá kích thước tệp khai báo ban đầu!");
            }

            // 1. Ghi dữ liệu vào đĩa cứng
            raf.seek(offset);
            raf.write(data, 0, length);

            // 2. Cập nhật mã băm lũy tiến vào MessageDigest trong RAM
            session.updateDigest(data, 0, length);

            // 3. Cập nhật vị trí tiếp theo
            long nextOffset = offset + length;
            session.setCurrentOffset(nextOffset);
            return nextOffset;
        } finally {
            session.unlock();
        }
    }

    /**
     * Hoàn tất lượt tải lên khi nhận lệnh UPLOAD_COMMIT.
     * Kiểm tra tính toàn vẹn tức thì và đổi tên file nguyên tử.
     */
    public synchronized FileInfo finishUpload() throws RequestException, IOException {
        close(); // Đóng file tạm trước khi đổi tên

        session.lock();
        try {
            if (session.getCurrentOffset() < session.getFileSize()) {
                throw new RequestException(Protocol.STATUS_BAD_REQUEST,
                        "Tệp chưa được truyền đầy đủ! (Đã nhận: " + session.getCurrentOffset() +
                                "/" + session.getFileSize() + " bytes)");
            }

            // 1. Lấy kết quả băm SHA-256 cuối cùng tức thì từ RAM
            byte[] finalHash = session.getSha256Digest().digest();

            // 2. So sánh với mã băm file nguồn do client cung cấp
            if (!HashUtils.matches(finalHash, session.getFileHash())) {
                // Xóa file tạm hỏng và hoàn trả quota
                fileStorage.deleteTempFile(session.getSessionId());
                fileManager.releaseReservedSpace(session.getFileSize());
                throw new RequestException(Protocol.STATUS_HASH_MISMATCH,
                        "Mã băm SHA-256 không trùng khớp! Tệp có thể đã bị lỗi trong quá trình truyền.");
            }

            // 3. Sinh fileId duy nhất và đổi tên nguyên tử thành file chính thức
            String fileId = "file-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            fileStorage.saveCompletedFile(session.getSessionId(), fileId);

            FileInfo completedInfo = new FileInfo(
                    fileId, session.getDisplayName(), session.getFileSize(),
                    finalHash, System.currentTimeMillis());

            // 4. Cập nhật danh mục và chuyển Quota từ reserved sang used
            fileManager.addFile(completedInfo);
            fileManager.confirmSpace(session.getFileSize());

            // 5. Đánh dấu session hoàn tất
            session.markCompleted(fileId);

            return completedInfo;
        } finally {
            session.unlock();
        }
    }

    @Override
    public synchronized void close() {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
            raf = null;
        }
    }
}

