package lftp.common;

/**
 * Đặc tả các hằng số, mã lệnh (Opcode), mã trạng thái (Status code) và các giới hạn
 * của giao thức truyền tệp LFTP/1 (LAN File Transfer Protocol Version 1).
 */
public final class Protocol {

    private Protocol() {
        // Ngăn chặn khởi tạo đối tượng tiện ích
    }

    // --- PHIÊN BẢN GIAO THỨC ---
    public static final byte VERSION = 1;

    // --- CỔNG MẶNG MẶC ĐỊNH ---
    public static final int DEFAULT_TCP_PORT = 5000;
    public static final int DEFAULT_UDP_PORT = 5001;

    // --- CÁC MÃ LỆNH (OPCODES) ---
    public static final byte OP_HELLO           = 1;   // Bắt tay thống nhất tham số
    public static final byte OP_LIST_FILES      = 2;   // Lấy danh mục file có phân trang
    public static final byte OP_QUIT            = 3;   // Đóng kết nối chủ động

    public static final byte OP_UPLOAD_INIT     = 16;  // Khởi tạo lượt tải lên mới
    public static final byte OP_UPLOAD_RESUME   = 17;  // Tiếp tục lượt tải lên dở
    public static final byte OP_UPLOAD_CHUNK    = 18;  // Truyền một chunk dữ liệu
    public static final byte OP_UPLOAD_COMMIT   = 19;  // Chốt hoàn tất lượt tải lên

    public static final byte OP_DOWNLOAD_OPEN   = 32;  // Mở lượt tải xuống (hỗ trợ offset)
    public static final byte OP_DOWNLOAD_NEXT   = 33;  // Kéo chunk dữ liệu tiếp theo
    public static final byte OP_DOWNLOAD_CLOSE  = 34;  // Đóng lượt tải xuống và báo kết quả

    // --- MÃ TRẠNG THÁI (STATUS CODES) ---
    public static final int STATUS_REQUEST              = 0;   // Dành cho yêu cầu từ Client
    public static final int STATUS_OK                   = 200; // Thành công
    public static final int STATUS_CREATED              = 201; // Đã tạo mới tài nguyên (Upload Session)

    public static final int STATUS_BAD_REQUEST          = 400; // Cấu trúc gói sai hoặc tham số không hợp lệ
    public static final int STATUS_FORBIDDEN            = 403; // Sai resumeToken, truy cập bị từ chối
    public static final int STATUS_NOT_FOUND            = 404; // Không tìm thấy file hoặc session
    public static final int STATUS_CONFLICT             = 409; // Lệch offset hoặc session đang có kết nối khác
    public static final int STATUS_TOO_LARGE            = 413; // Dung lượng file vượt quá 2 GiB
    public static final int STATUS_HASH_MISMATCH        = 422; // Sai mã băm SHA-256

    public static final int STATUS_INTERNAL_ERROR       = 500; // Lỗi hệ thống nội bộ máy chủ
    public static final int STATUS_UNKNOWN_COMMAND      = 501; // Mã lệnh không được hỗ trợ
    public static final int STATUS_UNSUPPORTED_VERSION  = 505; // Sai phiên bản giao thức (version != 1)
    public static final int STATUS_STORAGE_UNAVAILABLE  = 507; // Hết hạn ngạch (Quota 10 GiB) hoặc lỗi đĩa

    // --- TRẠNG THÁI PHIÊN TẢI LÊN (UPLOAD STATE) ---
    public static final byte UPLOAD_STATE_IN_PROGRESS   = 1;   // Lượt tải đang tiến hành
    public static final byte UPLOAD_STATE_COMPLETED     = 2;   // Lượt tải đã hoàn tất trước đó

    // --- CÁC HẠN MỨC & THÔNG SỐ AN TOÀN ---
    public static final int HEADER_FIXED_BYTES          = 12;     // 1B version + 1B opcode + 8B reqId + 2B status
    public static final int MIN_FRAME_BYTES             = 12;     // Ít nhất phải đủ header cố định
    public static final int MAX_FRAME_BYTES             = 131072; // 128 KiB (chưa tính 4 byte frameLength)
    public static final int CHUNK_SIZE                  = 65536;  // 64 KiB (kích thước dữ liệu truyền tối ưu LAN)
    public static final long MAX_FILE_BYTES             = 5368709120L;   // 5 GiB (giới hạn tối đa một file)
    public static final long STORAGE_QUOTA_BYTES        = 26843545600L;  // 25 GiB (tổng hạn ngạch lưu trữ server)
    public static final int MAX_CLIENTS                 = 20;     // Số kết nối TCP đồng thời tối đa
    public static final int MAX_CATALOG_FILES           = 10000;  // Số lượng file tối đa quản lý
    public static final int MAX_UPLOAD_SESSIONS         = 1000;   // Số lượng session upload đồng thời tối đa

    // --- THỜI GIAN CHỜ (TIMEOUTS) ---
    public static final int TIMEOUT_CONNECT_MS          = 5000;   // 5 giây (client kết nối TCP)
    public static final int TIMEOUT_HANDSHAKE_MS        = 10000;  // 10 giây (chờ lệnh HELLO sau accept)
    public static final int TIMEOUT_FRAME_MS            = 15000;  // 15 giây (chờ nhận đủ một khung 128 KiB)
    public static final int TIMEOUT_READ_MS             = 60000;  // 60 giây (socket read timeout)
    public static final long INACTIVE_UPLOAD_TTL_MS     = 30 * 60 * 1000L; // 30 phút (hủy session dở bị bỏ rơi)
    public static final long COMPLETED_UPLOAD_TTL_MS    = 24 * 60 * 60 * 1000L; // 24 giờ (giữ kết quả hoàn tất)

    // --- UDP BROADCAST DISCOVERY ---
    public static final String DISCOVERY_REQUEST        = "LFTP_DISCOVER";
    public static final String DISCOVERY_OFFER_PREFIX   = "LFTP_OFFER";
}

