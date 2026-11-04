package lftp.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Tiện ích tính toán và so khớp mã băm SHA-256 (32 byte nhị phân thô).
 */
public final class HashUtils {

    private HashUtils() {
    }

    /**
     * Tạo một đối tượng MessageDigest SHA-256 mới.
     */
    public static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hệ thống Java không hỗ trợ thuật toán SHA-256 tiêu chuẩn!", e);
        }
    }

    /**
     * Tính toán mã băm SHA-256 của toàn bộ một file cục bộ trên đĩa.
     */
    public static byte[] calculateFileSha256(File file) throws IOException {
        MessageDigest digest = createSha256Digest();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[Protocol.CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return digest.digest();
    }

    /**
     * Tính toán mã băm SHA-256 của một file tạm từ byte 0 đến byte limitOffset (phục vụ nạp lại digest khi resume).
     */
    public static MessageDigest resumeSha256State(File file, long limitOffset) throws IOException {
        MessageDigest digest = createSha256Digest();
        if (limitOffset <= 0) {
            return digest;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[Protocol.CHUNK_SIZE];
            long totalRead = 0;
            while (totalRead < limitOffset) {
                int toRead = (int) Math.min(buffer.length, limitOffset - totalRead);
                int bytesRead = fis.read(buffer, 0, toRead);
                if (bytesRead == -1) {
                    break;
                }
                digest.update(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        }
        return digest;
    }

    /**
     * Chuyển đổi mảng 32 byte nhị phân thành chuỗi Hex 64 ký tự.
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Chuyển đổi chuỗi Hex 64 ký tự thành mảng 32 byte nhị phân.
     */
    public static byte[] fromHexString(String hex) {
        if (hex == null || hex.length() != 64) {
            throw new IllegalArgumentException("Chuỗi hex SHA-256 bắt buộc phải có đúng 64 ký tự!");
        }
        byte[] data = new byte[32];
        for (int i = 0; i < 64; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * So sánh an toàn hai mảng mã băm SHA-256.
     */
    public static boolean matches(byte[] hash1, byte[] hash2) {
        return Arrays.equals(hash1, hash2);
    }
}

