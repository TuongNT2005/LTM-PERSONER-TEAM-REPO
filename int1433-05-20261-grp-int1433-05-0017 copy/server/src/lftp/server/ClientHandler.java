package lftp.server;

import lftp.common.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xử lý kết nối của một Client trên một Worker Thread trong Thread Pool.
 * Điều phối máy trạng thái (State Machine): Connected -> Ready -> Uploading/Downloading -> Closing.
 */
public class ClientHandler implements Runnable {

    public enum State {
        CONNECTED,
        READY,
        UPLOADING,
        DOWNLOADING,
        CLOSING,
        CLOSED
    }

    private final Socket socket;
    private final ServerConfig config;
    private final FileManager fileManager;
    private final FileStorage fileStorage;
    private final SessionManager sessionManager;
    private final ConnectionListener listener;

    private final String connectionId;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile State state = State.CONNECTED;
    private volatile long lastActiveTimeMs = System.currentTimeMillis();

    private MessageIO messageIO;
    private String clientName = "Unknown";
    private UploadManager activeUpload;
    private DownloadManager activeDownload;
    private String currentUploadId;

    public ClientHandler(Socket socket, ServerConfig config, FileManager fileManager,
                         FileStorage fileStorage, SessionManager sessionManager,
                         ConnectionListener listener) {
        this.socket = socket;
        this.config = config;
        this.fileManager = fileManager;
        this.fileStorage = fileStorage;
        this.sessionManager = sessionManager;
        this.listener = listener;
        this.connectionId = UUID.randomUUID().toString();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public long getLastActiveTimeMs() {
        return lastActiveTimeMs;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(Protocol.TIMEOUT_READ_MS);
            messageIO = new MessageIO(socket.getInputStream(), socket.getOutputStream());

            // 1. Bước bắt tay (Handshake) bắt buộc trong vòng 10 giây
            socket.setSoTimeout(Protocol.TIMEOUT_HANDSHAKE_MS);
            Message firstMsg = messageIO.readMessage();
            if (firstMsg.getVersion() != Protocol.VERSION) {
                sendError(firstMsg.getOpcode(), firstMsg.getRequestId(),
                        Protocol.STATUS_UNSUPPORTED_VERSION, "Phiên bản giao thức không hỗ trợ!");
                return;
            }
            if (firstMsg.getOpcode() != Protocol.OP_HELLO) {
                sendError(firstMsg.getOpcode(), firstMsg.getRequestId(),
                        Protocol.STATUS_BAD_REQUEST, "Yêu cầu đầu tiên bắt buộc phải là HELLO!");
                return;
            }
            handleHello(firstMsg);
            socket.setSoTimeout(Protocol.TIMEOUT_READ_MS); // Chuyển về timeout 60s thông thường

            // 2. Vòng lặp nhận và xử lý lệnh
            while (!closed.get() && state != State.CLOSING && state != State.CLOSED) {
                Message request;
                try {
                    request = messageIO.readMessage();
                } catch (SocketTimeoutException e) {
                    System.out.printf("[%s] Kết nối quá hạn thời gian chờ (60s không có dữ liệu), ngắt kết nối.%n", clientName);
                    break;
                } catch (EOFException e) {
                    break; // Client đóng kết nối chủ động
                }

                lastActiveTimeMs = System.currentTimeMillis();
                dispatchRequest(request);
            }

        } catch (Exception e) {
            if (!closed.get()) {
                System.out.printf("[%s] Lỗi kết nối mạng: %s%n", clientName, e.getMessage());
            }
        } finally {
            close();
        }
    }

    private void dispatchRequest(Message req) throws IOException {
        try {
            switch (req.getOpcode()) {
                case Protocol.OP_HELLO -> handleHello(req);
                case Protocol.OP_LIST_FILES -> handleListFiles(req);
                case Protocol.OP_QUIT -> handleQuit(req);
                case Protocol.OP_UPLOAD_INIT -> handleUploadInit(req);
                case Protocol.OP_UPLOAD_RESUME -> handleUploadResume(req);
                case Protocol.OP_UPLOAD_CHUNK -> handleUploadChunk(req);
                case Protocol.OP_UPLOAD_COMMIT -> handleUploadCommit(req);
                case Protocol.OP_DOWNLOAD_OPEN -> handleDownloadOpen(req);
                case Protocol.OP_DOWNLOAD_NEXT -> handleDownloadNext(req);
                case Protocol.OP_DOWNLOAD_CLOSE -> handleDownloadClose(req);
                default -> sendError(req.getOpcode(), req.getRequestId(),
                        Protocol.STATUS_UNKNOWN_COMMAND, "Mã lệnh (Opcode) không hỗ trợ: " + req.getOpcode());
            }
        } catch (RequestException re) {
            sendError(req.getOpcode(), req.getRequestId(), re.getStatusCode(), re.getMessage(), re.getExpectedOffset());
        } catch (Exception e) {
            sendError(req.getOpcode(), req.getRequestId(), Protocol.STATUS_INTERNAL_ERROR, "Lỗi máy chủ nội bộ: " + e.getMessage());
        }
    }

    // --- CÁC HÀM XỬ LÝ LỆNH NGHIỆP VỤ ---

    private void handleHello(Message req) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        this.clientName = MessageIO.readString(in);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, config.getServerName());
        MessageIO.writeString(out, connectionId);
        out.writeInt(config.getChunkSize());
        out.writeLong(config.getMaxFileBytes());
        out.writeInt(Protocol.MAX_FRAME_BYTES);
        out.flush();

        state = State.READY;
        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
        System.out.printf("[Server] Bắt tay thành công với Client: %s (ConnId: %s)%n", clientName, connectionId);
    }

    private void handleListFiles(Message req) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        int pageIndex = in.readInt();
        int pageSize = in.readInt();

        List<FileInfo> files = fileManager.listFiles(pageIndex, pageSize);
        int totalFiles = fileManager.getTotalFiles();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(totalFiles);
        out.writeInt(pageIndex);
        out.writeInt(files.size());
        for (FileInfo fi : files) {
            fi.writeToStream(out);
        }
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
    }

    private void handleQuit(Message req) throws IOException {
        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, new byte[0]));
        state = State.CLOSING;
    }

    private void handleUploadInit(Message req) throws RequestException, IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        String displayName = MessageIO.readString(in);
        long fileSize = in.readLong();
        byte[] fileHash = new byte[32];
        in.readFully(fileHash);

        TransferSession session = sessionManager.createUploadSession(displayName, fileSize, fileHash, connectionId);
        this.currentUploadId = session.getSessionId();
        this.activeUpload = new UploadManager(session, fileStorage, fileManager);
        this.state = State.UPLOADING;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, session.getSessionId());
        MessageIO.writeString(out, session.getResumeToken());
        out.writeLong(0L); // nextOffset = 0
        out.writeInt(config.getChunkSize());
        out.writeByte(Protocol.UPLOAD_STATE_IN_PROGRESS);
        MessageIO.writeString(out, ""); // fileId rỗng
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_CREATED, baos.toByteArray()));
    }

    private void handleUploadResume(Message req) throws RequestException, IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        String uploadId = MessageIO.readString(in);
        String resumeToken = MessageIO.readString(in);
        long fileSize = in.readLong();
        byte[] fileHash = new byte[32];
        in.readFully(fileHash);

        TransferSession session = sessionManager.resumeUploadSession(uploadId, resumeToken, fileSize, fileHash, connectionId);
        this.currentUploadId = session.getSessionId();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, session.getSessionId());
        MessageIO.writeString(out, session.getResumeToken());

        if (session.getUploadState() == Protocol.UPLOAD_STATE_COMPLETED) {
            out.writeLong(session.getFileSize());
            out.writeInt(config.getChunkSize());
            out.writeByte(Protocol.UPLOAD_STATE_COMPLETED);
            MessageIO.writeString(out, session.getCompletedFileId());
            this.state = State.READY;
        } else {
            this.activeUpload = new UploadManager(session, fileStorage, fileManager);
            this.state = State.UPLOADING;
            out.writeLong(session.getCurrentOffset());
            out.writeInt(config.getChunkSize());
            out.writeByte(Protocol.UPLOAD_STATE_IN_PROGRESS);
            MessageIO.writeString(out, "");
        }
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
    }

    private void handleUploadChunk(Message req) throws RequestException, IOException {
        if (activeUpload == null) {
            throw new RequestException(Protocol.STATUS_BAD_REQUEST, "Chưa khởi tạo hoặc mở lượt tải lên!");
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        String uploadId = MessageIO.readString(in);
        String resumeToken = MessageIO.readString(in);
        long offset = in.readLong();
        int dataLength = in.readInt();
        byte[] data = new byte[dataLength];
        in.readFully(data);

        long nextOffset = activeUpload.writeChunk(offset, data, dataLength);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeLong(nextOffset);
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
    }

    private void handleUploadCommit(Message req) throws RequestException, IOException {
        if (activeUpload == null) {
            throw new RequestException(Protocol.STATUS_BAD_REQUEST, "Không có lượt tải lên nào đang hoạt động!");
        }

        FileInfo completed = activeUpload.finishUpload();
        activeUpload = null;
        currentUploadId = null;
        state = State.READY;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, completed.getFileId());
        out.writeLong(completed.getFileSize());
        out.write(completed.getFileHash());
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
        System.out.printf("[Server] Đã hoàn tất tải lên tệp: %s (ID: %s, %.2f MB)%n",
                completed.getDisplayName(), completed.getFileId(), completed.getFileSize() / 1048576.0);
    }

    private void handleDownloadOpen(Message req) throws RequestException, IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        String fileId = MessageIO.readString(in);
        long requestedOffset = in.readLong();

        FileInfo fi = fileManager.findFile(fileId);
        if (fi == null) {
            throw new RequestException(Protocol.STATUS_NOT_FOUND, "Không tìm thấy tệp yêu cầu: " + fileId);
        }

        if (requestedOffset < 0 || requestedOffset > fi.getFileSize()) {
            throw new RequestException(Protocol.STATUS_BAD_REQUEST, "Offset yêu cầu vượt quá dung lượng tệp!");
        }

        if (activeDownload != null) {
            activeDownload.close();
        }
        activeDownload = new DownloadManager(fi, fileStorage);
        state = State.DOWNLOADING;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        MessageIO.writeString(out, fi.getFileId());
        MessageIO.writeString(out, fi.getDisplayName());
        out.writeLong(fi.getFileSize());
        out.write(fi.getFileHash());
        out.writeLong(requestedOffset);
        out.writeInt(config.getChunkSize());
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
    }

    private void handleDownloadNext(Message req) throws RequestException, IOException {
        if (activeDownload == null) {
            throw new RequestException(Protocol.STATUS_BAD_REQUEST, "Chưa mở lượt tải xuống!");
        }

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        long offset = in.readLong();
        int requestedBytes = in.readInt();

        byte[] chunkData = activeDownload.readChunk(offset, requestedBytes);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeLong(offset);
        out.writeInt(chunkData.length);
        out.write(chunkData);
        out.flush();

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, baos.toByteArray()));
    }

    private void handleDownloadClose(Message req) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(req.getBody()));
        int verified = in.readByte();

        if (activeDownload != null) {
            activeDownload.close();
            activeDownload = null;
        }
        state = State.READY;

        messageIO.writeMessage(Message.createResponse(req.getOpcode(), req.getRequestId(), Protocol.STATUS_OK, new byte[0]));
    }

    private void sendError(byte opcode, long requestId, int status, String message) throws IOException {
        sendError(opcode, requestId, status, message, -1L);
    }

    private void sendError(byte opcode, long requestId, int status, String message, long expectedOffset) throws IOException {
        byte[] body = MessageIO.createErrorBody(message, expectedOffset);
        messageIO.writeMessage(Message.createResponse(opcode, requestId, status, body));
    }

    /**
     * Đóng an toàn kết nối socket và giải phóng tài nguyên.
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            state = State.CLOSED;
            if (activeUpload != null) {
                activeUpload.close();
                activeUpload = null;
            }
            if (currentUploadId != null) {
                sessionManager.releaseConnection(currentUploadId, connectionId);
            }
            if (activeDownload != null) {
                activeDownload.close();
                activeDownload = null;
            }
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
            listener.onClientDisconnected(this);
        }
    }
}

