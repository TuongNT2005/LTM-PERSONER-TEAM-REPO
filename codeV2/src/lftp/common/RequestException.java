package lftp.common;

/**
 * Ngoại lệ đặc thù của giao thức LFTP/1, mang theo mã trạng thái (status code)
 * và vị trí byte mong đợi (expectedOffset) khi trả lỗi về cho client.
 */
public class RequestException extends Exception {

    private final int statusCode;
    private final long expectedOffset;

    public RequestException(int statusCode, String message) {
        this(statusCode, message, -1L);
    }

    public RequestException(int statusCode, String message, long expectedOffset) {
        super(message);
        this.statusCode = statusCode;
        this.expectedOffset = expectedOffset;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getExpectedOffset() {
        return expectedOffset;
    }

    @Override
    public String toString() {
        return "RequestException[status=" + statusCode + ", expectedOffset=" + expectedOffset + ", message=" + getMessage() + "]";
    }
}

