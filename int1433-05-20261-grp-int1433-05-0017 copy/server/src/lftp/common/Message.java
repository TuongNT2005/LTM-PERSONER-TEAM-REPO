package lftp.common;

import java.util.Arrays;

/**
 * Đối tượng đại diện cho một khung thông điệp đầy đủ trong giao thức LFTP/1.
 * Cấu trúc: [version (1B)][opcode (1B)][requestId (8B)][status (2B)][body (n bytes)]
 */
public class Message {

    private final byte version;
    private final byte opcode;
    private final long requestId;
    private final int status; // u16 (0-65535)
    private final byte[] body;

    public Message(byte version, byte opcode, long requestId, int status, byte[] body) {
        this.version = version;
        this.opcode = opcode;
        this.requestId = requestId;
        this.status = status & 0xFFFF;
        this.body = (body != null) ? body : new byte[0];
    }

    public Message(byte opcode, long requestId, int status, byte[] body) {
        this(Protocol.VERSION, opcode, requestId, status, body);
    }

    /**
     * Tạo một thông điệp yêu cầu (Request) từ client (status mặc định là 0).
     */
    public static Message createRequest(byte opcode, long requestId, byte[] body) {
        return new Message(Protocol.VERSION, opcode, requestId, Protocol.STATUS_REQUEST, body);
    }

    /**
     * Tạo một thông điệp phản hồi (Response) thành công từ server.
     */
    public static Message createResponse(byte opcode, long requestId, int status, byte[] body) {
        return new Message(Protocol.VERSION, opcode, requestId, status, body);
    }

    public byte getVersion() {
        return version;
    }

    public byte getOpcode() {
        return opcode;
    }

    public long getRequestId() {
        return requestId;
    }

    public int getStatus() {
        return status;
    }

    public byte[] getBody() {
        return body;
    }

    public int getBodyLength() {
        return body.length;
    }

    public int getPayloadFrameLength() {
        return Protocol.HEADER_FIXED_BYTES + body.length;
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    @Override
    public String toString() {
        return "Message{" +
                "version=" + version +
                ", opcode=" + opcode +
                ", requestId=" + requestId +
                ", status=" + status +
                ", bodyLength=" + body.length +
                '}';
    }
}

