package lftp.client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Quản lý việc lưu trữ trạng thái các lượt truyền dở xuống đĩa máy Client.
 * Phục vụ cơ chế tự động khôi phục và truyền tiếp (Resume) khi người dùng kết nối lại.
 */
public class ResumeManager {

    public record UploadState(
            String uploadId,
            String resumeToken,
            String localFilePath,
            long fileSize,
            String fileHashHex,
            long lastOffset,
            long savedAtEpochMs
    ) {}

    private final File stateDir;

    public ResumeManager(File stateDir) {
        this.stateDir = stateDir;
        if (!this.stateDir.exists()) {
            this.stateDir.mkdirs();
        }
    }

    private String getFileNameKey(String localFilePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(localFilePath.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(localFilePath.hashCode());
        }
    }

    public synchronized void saveUploadState(UploadState state) {
        String key = getFileNameKey(state.localFilePath());
        File file = new File(stateDir, key + ".state");

        Properties props = new Properties();
        props.setProperty("uploadId", state.uploadId());
        props.setProperty("resumeToken", state.resumeToken());
        props.setProperty("localFilePath", state.localFilePath());
        props.setProperty("fileSize", String.valueOf(state.fileSize()));
        props.setProperty("fileHashHex", state.fileHashHex());
        props.setProperty("lastOffset", String.valueOf(state.lastOffset()));
        props.setProperty("savedAtEpochMs", String.valueOf(System.currentTimeMillis()));

        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, "LFTP Upload Resume State");
        } catch (IOException e) {
            System.err.println("[ResumeManager] Không thể lưu file state: " + e.getMessage());
        }
    }

    public synchronized UploadState loadUploadState(String localFilePath) {
        String key = getFileNameKey(localFilePath);
        File file = new File(stateDir, key + ".state");
        if (!file.exists()) {
            return null;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            return new UploadState(
                    props.getProperty("uploadId"),
                    props.getProperty("resumeToken"),
                    props.getProperty("localFilePath"),
                    Long.parseLong(props.getProperty("fileSize")),
                    props.getProperty("fileHashHex"),
                    Long.parseLong(props.getProperty("lastOffset", "0")),
                    Long.parseLong(props.getProperty("savedAtEpochMs", "0"))
            );
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void removeUploadState(String localFilePath) {
        String key = getFileNameKey(localFilePath);
        File file = new File(stateDir, key + ".state");
        if (file.exists()) {
            file.delete();
        }
    }

    public synchronized List<UploadState> listPendingUploads() {
        List<UploadState> list = new ArrayList<>();
        File[] files = stateDir.listFiles((dir, name) -> name.endsWith(".state"));
        if (files != null) {
            for (File f : files) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(f)) {
                    props.load(fis);
                    list.add(new UploadState(
                            props.getProperty("uploadId"),
                            props.getProperty("resumeToken"),
                            props.getProperty("localFilePath"),
                            Long.parseLong(props.getProperty("fileSize")),
                            props.getProperty("fileHashHex"),
                            Long.parseLong(props.getProperty("lastOffset", "0")),
                            Long.parseLong(props.getProperty("savedAtEpochMs", "0"))
                    ));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }
}

