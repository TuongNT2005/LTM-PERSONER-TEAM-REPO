package lftp.client;

import lftp.common.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Công nhân mạng phía Client (Network Worker - Luồng 2).
 * Chuyên trách gửi nhận các khung nhị phân LFTP/1 qua TCP Socket,
 * hỗ trợ tạm dừng, hủy và khôi phục (Resume) truyền tải.
 */
public class TransferClient implements AutoCloseable {

    public record ListResult(int totalFiles, int pageIndex, int count, List<FileInfo> entries) {}

    private final ClientConfig config;
    private final ResumeManager resumeManager;
    private final AtomicLong requestIdGen = new AtomicLong(1);

    private Socket socket;
    private MessageIO messageIO;
    private String serverName;
    private String connectionId;
    private int negotiatedChunkSize = Protocol.CHUNK_SIZE;

    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;
    private volatile TransferProgress activeProgress;

    public TransferClient(ClientConfig config, ResumeManager resumeManager) {
        this.config = config;
        this.resumeManager = resumeManager;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getServerName() {
        return serverName;
    }

    public TransferProgress getActiveProgress() {
        return activeProgress;
    }

    public void pauseTransfer() {
        this.isPaused = true;
    }

    public void resumeTransfer() {
        this.isPaused = false;
    }

    public void cancelTransfer() {
        this.isCancelled = true;
    }

    /**
     * Kết nối TCP tới Server và thực hiện bắt tay (HELLO).
     */
    public synchronized void connect(String host, int port) throws IOException, RequestException {
        close();
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), Protocol.TIMEOUT_CONNECT_MS);
        this.socket.setSoTimeout(Protocol.TIMEOUT_READ_MS);
        this.messageIO = new MessageIO(socket.getInputStream(), socket.getOutputStream());

        // Gửi HELLO bắt tay
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, config.getClientName());
        out.flush();

        long reqId = requestIdGen.getAndIncrement();
        messageIO.writeMessage(Message.createRequest(Protocol.OP_HELLO, reqId, baos.toByteArray()));

        Message resp = messageIO.readMessage();
        if (resp.getStatus() != Protocol.STATUS_OK) {
            throw MessageIO.parseError(resp);
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
        this.serverName = MessageIO.readString(in);
        this.connectionId = MessageIO.readString(in);
        this.negotiatedChunkSize = in.readInt();
        config.setServerHost(host);
        config.setServerPort(port);

        System.out.printf("[Client] Kết nối thành công tới Server: %s (IP: %s:%d, ConnId: %s)%n",
                serverName, host, port, connectionId);
    }

    /**
     * Lấy danh sách tệp hoàn tất có phân trang từ Server.
     */
    public synchronized ListResult listFiles(int pageIndex, int pageSize) throws IOException, RequestException {
        ensureConnected();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(pageIndex);
        out.writeInt(pageSize);
        out.flush();

        long reqId = requestIdGen.getAndIncrement();
        messageIO.writeMessage(Message.createRequest(Protocol.OP_LIST_FILES, reqId, baos.toByteArray()));

        Message resp = messageIO.readMessage();
        if (resp.getStatus() != Protocol.STATUS_OK) {
            throw MessageIO.parseError(resp);
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
        int totalFiles = in.readInt();
        int page = in.readInt();
        int count = in.readInt();

        List<FileInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(FileInfo.readFromStream(in));
        }
        return new ListResult(totalFiles, page, count, list);
    }

    /**
     * Tải lên một tệp với thanh tiến độ thời gian thực và hỗ trợ Resume.
     */
    public synchronized String uploadFile(File file) throws Exception {
        ensureConnected();
        isPaused = false;
        isCancelled = false;

        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Tệp nguồn không tồn tại: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        if (fileSize > Protocol.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Dung lượng tệp (" + TransferProgress.formatBytes(fileSize) + 
                    ") vượt quá giới hạn " + (Protocol.MAX_FILE_BYTES / (1024L * 1024L * 1024L)) + " GiB cho phép!");
        }

        System.out.println("[Upload] Đang tính toán mã băm SHA-256 của tệp nguồn...");
        byte[] fileHash = HashUtils.calculateFileSha256(file);
        String fileHashHex = HashUtils.toHexString(fileHash);
        System.out.println("[Upload] SHA-256 nguồn: " + fileHashHex);

        String uploadId;
        String resumeToken;
        long startOffset = 0;

        // 1. Kiểm tra xem có trạng thái truyền dở trước đó không để Resume
        ResumeManager.UploadState savedState = resumeManager.loadUploadState(file.getAbsolutePath());
        if (savedState != null && savedState.fileHashHex().equalsIgnoreCase(fileHashHex)) {
            System.out.printf("[Upload] Phát hiện phiên dở trước đó (ID: %s, Vị trí đã lưu: %s). Đang xin resume...%n",
                    savedState.uploadId(), TransferProgress.formatBytes(savedState.lastOffset()));
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);
                MessageIO.writeString(out, savedState.uploadId());
                MessageIO.writeString(out, savedState.resumeToken());
                out.writeLong(fileSize);
                out.write(fileHash);
                out.flush();

                long reqId = requestIdGen.getAndIncrement();
                messageIO.writeMessage(Message.createRequest(Protocol.OP_UPLOAD_RESUME, reqId, baos.toByteArray()));

                Message resp = messageIO.readMessage();
                if (resp.getStatus() == Protocol.STATUS_OK) {
                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
                    uploadId = MessageIO.readString(in);
                    resumeToken = MessageIO.readString(in);
                    startOffset = in.readLong();
                    int chunkSize = in.readInt();
                    byte uploadState = in.readByte();
                    String completedFileId = MessageIO.readString(in);

                    if (uploadState == Protocol.UPLOAD_STATE_COMPLETED) {
                        System.out.println("[Upload] Tệp này đã được hoàn tất trên server trước đó! (FileID: " + completedFileId + ")");
                        resumeManager.removeUploadState(file.getAbsolutePath());
                        return completedFileId;
                    }
                    System.out.printf("[Upload] Server đồng ý Resume từ vị trí byte: %d (%s)%n",
                            startOffset, TransferProgress.formatBytes(startOffset));
                } else {
                    System.out.println("[Upload] Server từ chối resume (" + resp.getStatus() + "), tiến hành upload mới.");
                    savedState = null;
                    resumeManager.removeUploadState(file.getAbsolutePath());
                }
            } catch (Exception e) {
                System.out.println("[Upload] Lỗi khi xin resume, thử upload mới: " + e.getMessage());
                savedState = null;
            }
        }

        // 2. Khởi tạo Upload mới nếu không resume
        if (savedState == null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            MessageIO.writeString(out, file.getName());
            out.writeLong(fileSize);
            out.write(fileHash);
            out.flush();

            long reqId = requestIdGen.getAndIncrement();
            messageIO.writeMessage(Message.createRequest(Protocol.OP_UPLOAD_INIT, reqId, baos.toByteArray()));

            Message resp = messageIO.readMessage();
            if (resp.getStatus() != Protocol.STATUS_CREATED) {
                throw MessageIO.parseError(resp);
            }

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
            uploadId = MessageIO.readString(in);
            resumeToken = MessageIO.readString(in);
            startOffset = in.readLong(); // = 0

            // Lưu trạng thái sơ khởi
            resumeManager.saveUploadState(new ResumeManager.UploadState(
                    uploadId, resumeToken, file.getAbsolutePath(), fileSize, fileHashHex, 0, System.currentTimeMillis()));
        } else {
            uploadId = savedState.uploadId();
            resumeToken = savedState.resumeToken();
        }

        // 3. Vòng lặp truyền từng chunk 64 KiB
        activeProgress = new TransferProgress(file.getName(), fileSize, startOffset);
        long currentOffset = startOffset;
        byte[] chunkBuffer = new byte[negotiatedChunkSize];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(currentOffset);

            while (currentOffset < fileSize) {
                if (isCancelled) {
                    System.out.println("\n[Upload] Người dùng đã hủy lượt truyền tệp!");
                    resumeManager.removeUploadState(file.getAbsolutePath());
                    return null;
                }

                while (isPaused) {
                    Thread.sleep(200);
                    if (isCancelled) break;
                }

                int toRead = (int) Math.min(chunkBuffer.length, fileSize - currentOffset);
                raf.readFully(chunkBuffer, 0, toRead);

                // Đóng gói UPLOAD_CHUNK
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos);
                MessageIO.writeString(out, uploadId);
                MessageIO.writeString(out, resumeToken);
                out.writeLong(currentOffset);
                out.writeInt(toRead);
                out.write(chunkBuffer, 0, toRead);
                out.flush();

                long reqId = requestIdGen.getAndIncrement();
                messageIO.writeMessage(Message.createRequest(Protocol.OP_UPLOAD_CHUNK, reqId, baos.toByteArray()));

                Message resp = messageIO.readMessage();
                if (resp.getStatus() != Protocol.STATUS_OK) {
                    throw MessageIO.parseError(resp);
                }

                DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
                long nextOffset = in.readLong();
                currentOffset = nextOffset;

                activeProgress.update(currentOffset);
                System.out.print(activeProgress.renderProgressBar(30));

                // Cập nhật trạng thái đĩa client định kỳ mỗi 1 MB
                if (currentOffset % (1024 * 1024) < negotiatedChunkSize) {
                    resumeManager.saveUploadState(new ResumeManager.UploadState(
                            uploadId, resumeToken, file.getAbsolutePath(), fileSize, fileHashHex, currentOffset, System.currentTimeMillis()));
                }
            }
        }

        System.out.println("\n[Upload] Đã truyền đủ 100% dữ liệu. Đang gửi COMMIT xác thực toàn vẹn...");

        // 4. Gửi UPLOAD_COMMIT
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, uploadId);
        MessageIO.writeString(out, resumeToken);
        out.flush();

        long reqId = requestIdGen.getAndIncrement();
        messageIO.writeMessage(Message.createRequest(Protocol.OP_UPLOAD_COMMIT, reqId, baos.toByteArray()));

        Message resp = messageIO.readMessage();
        if (resp.getStatus() != Protocol.STATUS_OK) {
            throw MessageIO.parseError(resp);
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
        String finalFileId = MessageIO.readString(in);
        long confirmedSize = in.readLong();
        byte[] confirmedHash = new byte[32];
        in.readFully(confirmedHash);

        // Xóa trạng thái dở sau khi thành công
        resumeManager.removeUploadState(file.getAbsolutePath());
        System.out.printf("[Upload] HOÀN TẤT THÀNH CÔNG! FileId: %s (Đã kiểm tra mã SHA-256 khớp 100%%)%n", finalFileId);
        return finalFileId;
    }

    /**
     * Tải về một tệp từ Server với thanh tiến độ thời gian thực.
     */
    public synchronized File downloadFile(String fileId, File targetDir) throws Exception {
        ensureConnected();
        isPaused = false;
        isCancelled = false;

        // 1. Mở lượt tải xuống
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, fileId);
        out.writeLong(0L); // requestedOffset = 0
        out.flush();

        long reqId = requestIdGen.getAndIncrement();
        messageIO.writeMessage(Message.createRequest(Protocol.OP_DOWNLOAD_OPEN, reqId, baos.toByteArray()));

        Message resp = messageIO.readMessage();
        if (resp.getStatus() != Protocol.STATUS_OK) {
            throw MessageIO.parseError(resp);
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getBody()));
        String confirmedFileId = MessageIO.readString(in);
        String displayName = MessageIO.readString(in);
        long fileSize = in.readLong();
        byte[] expectedHash = new byte[32];
        in.readFully(expectedHash);
        long acceptedOffset = in.readLong();
        int chunkSize = in.readInt();

        if (targetDir == null) {
            targetDir = config.getDownloadDir();
        }
        File tempFile = new File(targetDir, displayName + ".part");
        File finalFile = new File(targetDir, displayName);

        System.out.printf("[Download] Bắt đầu tải tệp: %s (%s)%n", displayName, TransferProgress.formatBytes(fileSize));
        activeProgress = new TransferProgress(displayName, fileSize, acceptedOffset);
        long currentOffset = acceptedOffset;

        try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
            raf.seek(currentOffset);

            while (currentOffset < fileSize) {
                if (isCancelled) {
                    System.out.println("\n[Download] Người dùng đã hủy lượt tải về!");
                    tempFile.delete();
                    sendDownloadClose(false);
                    return null;
                }

                while (isPaused) {
                    Thread.sleep(200);
                    if (isCancelled) break;
                }

                int toRead = (int) Math.min(negotiatedChunkSize, fileSize - currentOffset);

                ByteArrayOutputStream reqBaos = new ByteArrayOutputStream();
                DataOutputStream reqOut = new DataOutputStream(reqBaos);
                reqOut.writeLong(currentOffset);
                reqOut.writeInt(toRead);
                reqOut.flush();

                long nextReqId = requestIdGen.getAndIncrement();
                messageIO.writeMessage(Message.createRequest(Protocol.OP_DOWNLOAD_NEXT, nextReqId, reqBaos.toByteArray()));

                Message chunkResp = messageIO.readMessage();
                if (chunkResp.getStatus() != Protocol.STATUS_OK) {
                    throw MessageIO.parseError(chunkResp);
                }

                DataInputStream chunkIn = new DataInputStream(new ByteArrayInputStream(chunkResp.getBody()));
                long returnedOffset = chunkIn.readLong();
                int dataLen = chunkIn.readInt();
                byte[] chunkData = new byte[dataLen];
                chunkIn.readFully(chunkData);

                raf.write(chunkData);
                currentOffset += dataLen;

                activeProgress.update(currentOffset);
                System.out.print(activeProgress.renderProgressBar(30));
            }
        }

        System.out.println("\n[Download] Đã nhận đủ 100% dữ liệu. Đang xác minh mã băm SHA-256...");
        byte[] downloadedHash = HashUtils.calculateFileSha256(tempFile);
        boolean matched = HashUtils.matches(downloadedHash, expectedHash);

        sendDownloadClose(matched);

        if (!matched) {
            tempFile.delete();
            throw new RequestException(Protocol.STATUS_HASH_MISMATCH,
                    "Mã băm SHA-256 tệp tải về không khớp với file nguồn trên server!");
        }

        if (finalFile.exists()) {
            finalFile.delete();
        }
        tempFile.renameTo(finalFile);
        System.out.printf("[Download] TẢI VỀ THÀNH CÔNG! Tệp lưu tại: %s%n", finalFile.getAbsolutePath());
        return finalFile;
    }

    private void sendDownloadClose(boolean verified) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(verified ? 1 : 0);
            long reqId = requestIdGen.getAndIncrement();
            messageIO.writeMessage(Message.createRequest(Protocol.OP_DOWNLOAD_CLOSE, reqId, baos.toByteArray()));
            messageIO.readMessage(); // Đọc 200 OK
        } catch (Exception ignored) {
        }
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Client chưa kết nối tới Server! Vui lòng dùng lệnh 'connect' hoặc 'discover'.");
        }
    }

    @Override
    public synchronized void close() {
        if (isConnected()) {
            try {
                long reqId = requestIdGen.getAndIncrement();
                messageIO.writeMessage(Message.createRequest(Protocol.OP_QUIT, reqId, new byte[0]));
            } catch (Exception ignored) {
            }
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        messageIO = null;
    }
}

