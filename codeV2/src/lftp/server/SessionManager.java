package lftp.server;

import lftp.common.HashUtils;
import lftp.common.Protocol;
import lftp.common.RequestException;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý vòng đời của các phiên truyền tải dữ liệu trong bộ nhớ RAM của Server.
 * Cấp phát resumeToken bí mật, bảo đảm một phiên dở chỉ có 1 kết nối ghi tại 1 thời điểm.
 */
public class SessionManager {

    private final Map<String, TransferSession> sessions = new ConcurrentHashMap<>();
    private final FileManager fileManager;
    private final FileStorage fileStorage;

    public SessionManager(FileManager fileManager, FileStorage fileStorage) {
        this.fileManager = fileManager;
        this.fileStorage = fileStorage;
    }

    /**
     * Khởi tạo một phiên tải lên mới.
     */
    public TransferSession createUploadSession(String displayName, long fileSize, byte[] fileHash, String connectionId)
            throws RequestException, IOException {
        if (sessions.size() >= Protocol.MAX_UPLOAD_SESSIONS) {
            throw new RequestException(Protocol.STATUS_STORAGE_UNAVAILABLE, "Server đạt giới hạn số lượng phiên tải lên đồng thời!");
        }

        // 1. Dành chỗ hạn ngạch trước bằng CAS không khóa
        fileManager.reserveSpace(fileSize);

        // 2. Tạo mã nhận diện và token ngẫu nhiên bảo mật
        String uploadId = "up-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String resumeToken = UUID.randomUUID().toString();

        // 3. Tạo file tạm vật lý trên đĩa
        fileStorage.openTempFile(uploadId).close();

        TransferSession session = new TransferSession(
                uploadId, resumeToken, TransferSession.Type.UPLOAD,
                displayName, fileSize, fileHash, 0L, connectionId);

        sessions.put(uploadId, session);
        return session;
    }

    /**
     * Nạp lại một phiên tải lên dở sau khi bị ngắt kết nối (Resume).
     */
    public TransferSession resumeUploadSession(String uploadId, String resumeToken,
                                               long fileSize, byte[] fileHash, String connectionId)
            throws RequestException, IOException {
        TransferSession session = sessions.get(uploadId);
        if (session == null) {
            throw new RequestException(Protocol.STATUS_NOT_FOUND, "Không tìm thấy phiên tải lên hoặc phiên đã hết hạn: " + uploadId);
        }

        session.lock();
        try {
            // Kiểm tra tính hợp lệ của token bí mật
            if (!session.getResumeToken().equals(resumeToken)) {
                throw new RequestException(Protocol.STATUS_FORBIDDEN, "Sai resumeToken! Bạn không có quyền truy cập phiên này.");
            }

            // Nếu phiên này đã được hoàn tất trước đó
            if (session.getUploadState() == Protocol.UPLOAD_STATE_COMPLETED) {
                return session;
            }

            // Kiểm tra xem có kết nối nào khác đang ghi đồng thời vào session này không
            String activeConn = session.getActiveConnectionId();
            if (activeConn != null && !activeConn.equals(connectionId)) {
                throw new RequestException(Protocol.STATUS_CONFLICT,
                        "Phiên tải lên này đang được xử lý bởi một kết nối khác: " + activeConn);
            }

            // Đối chiếu kích thước thực tế trên đĩa cứng
            long actualDiskSize = fileStorage.getTempFileSize(uploadId);
            session.setCurrentOffset(actualDiskSize);

            // Nạp lại trạng thái băm SHA-256 từ đầu file đến vị trí actualDiskSize
            File tempFile = fileStorage.getTempFile(uploadId);
            MessageDigest resumedDigest = HashUtils.resumeSha256State(tempFile, actualDiskSize);

            // Cập nhật lại digest của session bằng cách thay thế
            // Do MessageDigest không hỗ trợ reset sang state khác, ta dùng digest mới
            session.setSha256Digest(resumedDigest);
            session.assignConnection(connectionId);
            return session;
        } finally {
            session.unlock();
        }
    }

    public TransferSession findSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void releaseConnection(String uploadId, String connectionId) {
        TransferSession session = sessions.get(uploadId);
        if (session != null) {
            session.clearConnection(connectionId);
        }
    }

    /**
     * Thu hồi các phiên tải lên bị bỏ rơi quá 30 phút.
     * Áp dụng nguyên tắc Snapshot trước khi xử lý để tránh lồng khóa.
     */
    public void removeExpiredSessions() {
        long now = System.currentTimeMillis();
        List<TransferSession> expiredList = new ArrayList<>();

        // Bước 1: Trích xuất danh sách session hết hạn ra snapshot
        for (TransferSession session : sessions.values()) {
            if (session.getType() == TransferSession.Type.UPLOAD
                    && session.getUploadState() == Protocol.UPLOAD_STATE_IN_PROGRESS
                    && (now - session.getLastActiveEpochMs() > Protocol.INACTIVE_UPLOAD_TTL_MS)) {
                expiredList.add(session);
            }
        }

        // Bước 2: Xử lý thu hồi ngoài khóa
        for (TransferSession s : expiredList) {
            sessions.remove(s.getSessionId());
            fileStorage.deleteTempFile(s.getSessionId());
            fileManager.releaseReservedSpace(s.getFileSize());
            System.out.printf("[SessionManager] Đã thu hồi phiên hết hạn quá 30 phút: %s (Giải phóng %.2f MB quota)%n",
                    s.getSessionId(), s.getFileSize() / 1048576.0);
        }
    }
}

