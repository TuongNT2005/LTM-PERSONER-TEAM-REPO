package lftp.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Đối tượng chứa thông tin siêu dữ liệu (metadata) của một tệp đã hoàn tất trên máy chủ.
 */
public class FileInfo {

    private final String fileId;
    private final String displayName;
    private final long fileSize;
    private final byte[] fileHash; // Đúng 32 bytes SHA-256
    private final long createdAtEpochMillis;

    public FileInfo(String fileId, String displayName, long fileSize, byte[] fileHash, long createdAtEpochMillis) {
        if (fileHash == null || fileHash.length != 32) {
            throw new IllegalArgumentException("fileHash bắt buộc phải là mảng đúng 32 bytes SHA-256!");
        }
        this.fileId = fileId;
        this.displayName = displayName;
        this.fileSize = fileSize;
        this.fileHash = fileHash.clone();
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public String getFileId() {
        return fileId;
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

    public String getFileHashHex() {
        return HashUtils.toHexString(fileHash);
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    /**
     * Ghi đối tượng FileInfo ra luồng DataOutputStream theo chuẩn LFTP/1:
     * fileId: str (4B length + UTF-8)
     * displayName: str (4B length + UTF-8)
     * fileSize: long (8B)
     * fileHash: 32B thô
     * createdAt: long (8B)
     */
    public void writeToStream(DataOutputStream dos) throws IOException {
        writeString(dos, fileId);
        writeString(dos, displayName);
        dos.writeLong(fileSize);
        dos.write(fileHash);
        dos.writeLong(createdAtEpochMillis);
    }

    /**
     * Đọc đối tượng FileInfo từ DataInputStream.
     */
    public static FileInfo readFromStream(DataInputStream dis) throws IOException {
        String fileId = readString(dis);
        String displayName = readString(dis);
        long fileSize = dis.readLong();
        byte[] fileHash = new byte[32];
        dis.readFully(fileHash);
        long createdAt = dis.readLong();
        return new FileInfo(fileId, displayName, fileSize, fileHash, createdAt);
    }

    private static void writeString(DataOutputStream dos, String s) throws IOException {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    private static String readString(DataInputStream dis) throws IOException {
        int len = dis.readInt();
        if (len < 0 || len > Protocol.MAX_FRAME_BYTES) {
            throw new IOException("Độ dài chuỗi không hợp lệ: " + len);
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "fileId='" + fileId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", fileSize=" + fileSize +
                ", fileHash=" + getFileHashHex() +
                ", createdAt=" + createdAtEpochMillis +
                '}';
    }
}

