package lftp.common;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Tầng đóng gói và giải mã khung thông điệp (Binary Framing Layer).
 * Định dạng:
 * [frameLength (4B)][version (1B)][opcode (1B)][requestId (8B)][status (2B)][body (frameLength - 12B)]
 * Đảm bảo đọc đủ khung, chặn trần 128 KiB để chống cạn kiệt bộ nhớ Heap.
 */
public class MessageIO {

    private final DataInputStream dis;
    private final DataOutputStream dos;

    public MessageIO(InputStream in, OutputStream out) {
        this.dis = new DataInputStream(new BufferedInputStream(in, Protocol.CHUNK_SIZE));
        this.dos = new DataOutputStream(new BufferedOutputStream(out, Protocol.CHUNK_SIZE));
    }

    /**
     * Gửi một thông điệp ra luồng mạng.
     */
    public synchronized void writeMessage(Message message) throws IOException {
        int frameLength = message.getPayloadFrameLength();
        if (frameLength < Protocol.MIN_FRAME_BYTES || frameLength > Protocol.MAX_FRAME_BYTES) {
            throw new IOException("Kích thước khung không hợp lệ: " + frameLength + 
                    " (Phải từ " + Protocol.MIN_FRAME_BYTES + " đến " + Protocol.MAX_FRAME_BYTES + " bytes)");
        }

        // 1. Ghi 4 byte độ dài khung (không tính chính 4 byte này)
        dos.writeInt(frameLength);

        // 2. Ghi 12 byte Header cố định
        dos.writeByte(message.getVersion());
        dos.writeByte(message.getOpcode());
        dos.writeLong(message.getRequestId());
        dos.writeShort(message.getStatus() & 0xFFFF);

        // 3. Ghi phần body (nếu có)
        byte[] body = message.getBody();
        if (body != null && body.length > 0) {
            dos.write(body);
        }

        dos.flush();
    }

    /**
     * Đọc một thông điệp hoàn chỉnh từ luồng mạng.
     */
    public synchronized Message readMessage() throws IOException {
        // 1. Đọc 4 byte độ dài khung
        int frameLength = dis.readInt();
        if (frameLength < Protocol.MIN_FRAME_BYTES || frameLength > Protocol.MAX_FRAME_BYTES) {
            throw new IOException("Khung nhận được vi phạm giới hạn giao thức: " + frameLength + 
                    " bytes (Giới hạn tối đa " + Protocol.MAX_FRAME_BYTES + " bytes)");
        }

        // 2. Đọc 12 byte Header
        byte version = dis.readByte();
        byte opcode = dis.readByte();
        long requestId = dis.readLong();
        int status = dis.readUnsignedShort();

        // 3. Đọc đúng số byte còn lại của phần body
        int bodyLength = frameLength - Protocol.HEADER_FIXED_BYTES;
        byte[] body = new byte[bodyLength];
        if (bodyLength > 0) {
            dis.readFully(body);
        }

        return new Message(version, opcode, requestId, status, body);
    }

    // --- CÁC HÀM TIỆN ÍCH ĐÓNG GÓI DỮ LIỆU ĐẶC THÙ ---

    /**
     * Tạo phần body của một thông điệp lỗi chuẩn LFTP/1:
     * [message: str (4B length + UTF-8)][expectedOffset: long (8B)]
     */
    public static byte[] createErrorBody(String message, long expectedOffset) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            writeString(out, message);
            out.writeLong(expectedOffset);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Lỗi không tưởng khi đóng gói error body", e);
        }
    }

    /**
     * Giải mã thông điệp lỗi thành đối tượng RequestException.
     */
    public static RequestException parseError(Message response) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return new RequestException(response.getStatus(), "Lỗi từ server mã: " + response.getStatus());
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
            String msg = readString(in);
            long expectedOffset = -1;
            if (in.available() >= 8) {
                expectedOffset = in.readLong();
            }
            return new RequestException(response.getStatus(), msg, expectedOffset);
        } catch (IOException e) {
            return new RequestException(response.getStatus(), "Không thể giải mã lỗi từ server: " + e.getMessage());
        }
    }

    /**
     * Ghi một chuỗi UTF-8 tiêu chuẩn: [int 4 byte độ dài][mảng byte UTF-8].
     */
    public static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    /**
     * Đọc một chuỗi UTF-8 tiêu chuẩn.
     */
    public static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > Protocol.MAX_FRAME_BYTES) {
            throw new IOException("Độ dài chuỗi không hợp lệ: " + len);
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

