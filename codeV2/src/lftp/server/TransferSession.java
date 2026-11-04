package lftp.server;

import lftp.common.HashUtils;
import lftp.common.Protocol;

import java.security.MessageDigest;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Trạng thái của một phiên truyền tệp trong bộ nhớ máy chủ.
 * Chứa đối tượng MessageDigest để tính toán mã băm SHA-256 lũy tiến (Streaming Hash).
 */
public class TransferSession {

    public enum Type {
        UPLOAD,
        DOWNLOAD
    }

    private final String sessionId;
    private final String resumeToken; // Chuỗi token bí mật chống chiếm đoạt phiên
    private final Type type;
    private final String displayName;
    private final long fileSize;
    private final byte[] fileHash; // Đúng 32 bytes

    private long currentOffset;
    private volatile long lastActiveEpochMs;
    private volatile String activeConnectionId; // Kết nối TCP đang phụ trách
    private volatile byte uploadState; // 1: In-progress, 2: Completed
    private volatile String completedFileId;

    private MessageDigest sha256Digest;
    private final ReentrantLock lock = new ReentrantLock();

    public TransferSession(String sessionId, String resumeToken, Type type,
                           String displayName, long fileSize, byte[] fileHash,
                           long startOffset, String initialConnectionId) {
        this.sessionId = sessionId;
        this.resumeToken = resumeToken;
        this.type = type;
        this.displayName = displayName;
        this.fileSize = fileSize;
        this.fileHash = (fileHash != null) ? fileHash.clone() : new byte[32];
        this.currentOffset = startOffset;
        this.activeConnectionId = initialConnectionId;
        this.uploadState = Protocol.UPLOAD_STATE_IN_PROGRESS;
        this.completedFileId = null;
        this.lastActiveEpochMs = System.currentTimeMillis();
        this.sha256Digest = HashUtils.createSha256Digest();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public Type getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getFileHash() {
        return fileHash.clone();
    }

    public long getCurrentOffset() {
        return currentOffset;
    }

    public void setCurrentOffset(long offset) {
        this.currentOffset = offset;
    }

    public long getLastActiveEpochMs() {
        return lastActiveEpochMs;
    }

    public void touch() {
        this.lastActiveEpochMs = System.currentTimeMillis();
    }

    public String getActiveConnectionId() {
        return activeConnectionId;
    }

    public void assignConnection(String connectionId) {
        this.activeConnectionId = connectionId;
        touch();
    }

    public void clearConnection(String connectionId) {
        if (connectionId != null && connectionId.equals(this.activeConnectionId)) {
            this.activeConnectionId = null;
        }
        touch();
    }

    public byte getUploadState() {
        return uploadState;
    }

    public void markCompleted(String fileId) {
        this.uploadState = Protocol.UPLOAD_STATE_COMPLETED;
        this.completedFileId = fileId;
        this.activeConnectionId = null;
        touch();
    }

    public String getCompletedFileId() {
        return completedFileId;
    }

    public MessageDigest getSha256Digest() {
        return sha256Digest;
    }

    public void setSha256Digest(MessageDigest sha256Digest) {
        this.sha256Digest = sha256Digest;
    }

    /**
     * Cập nhật một mảng byte vào bộ băm lũy tiến SHA-256.
     */
    public void updateDigest(byte[] data, int offset, int length) {
        sha256Digest.update(data, offset, length);
        touch();
    }
}

