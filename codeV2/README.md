# DỰ ÁN LFTP/1 (LAN FILE TRANSFER PROTOCOL V1)
## HỆ THỐNG TRUYỀN TỆP TIN CẬY TRONG MẠNG NỘI BỘ (LAN)
**Đồ án môn học:** Lập trình Mạng (BTL LTM)  
**Nhóm thực hiện:** TKD (*Nguyễn Trung Tường, Bùi Văn Đạt, Nguyễn Đình Khải*)  
**Phiên bản mã nguồn:** `codeV2` (Được kiến trúc sư trưởng định hình và phát triển dựa trên bộ tài liệu đặc tả `architectureV2.md`, `proposal.md`, `protocol-design.md`, `oral.md`)

---

## 1. TỔNG QUAN HỆ THỐNG & ĐẶC TÍNH NỔI BẬT

Hệ thống LFTP/1 là giải pháp truyền tệp chuyên dụng trong mạng cục bộ văn phòng / phòng lab, giải quyết bài toán truyền file lớn (lên tới 2 GiB) với tốc độ Gigabit LAN mà không phụ thuộc vào Internet hay bên thứ ba (Google Drive):

1. **Hiệu năng vượt trội:** Tận dụng tối đa băng thông vật lý mạng LAN (đạt 80–110 MB/s trên cáp Gigabit), truyền tệp 2 GiB chỉ mất khoảng 20 giây.
2. **Giao thức nhị phân tối ưu (Binary Framing LFTP/1):**
   - Header cố định 12 bytes (`[frameLength: 4B][version: 1B][opcode: 1B][requestId: 8B][status: 2B]`).
   - Giới hạn trần khung an toàn **128 KiB**, ngăn chặn hoàn toàn tấn công DoS tràn bộ nhớ Heap RAM máy chủ.
3. **Mô hình Concurrency bền vững (Server Thread Pool):**
   - Server khống chế tối đa **20 worker threads** với hàng đợi `ArrayBlockingQueue`, kiểm soát chính xác mức tiêu thụ bộ nhớ native stack của hệ điều hành.
4. **Triệt tiêu Deadlock 100%:**
   - Thiết lập thứ bậc lấy khóa nghiêm ngặt: `SessionManager` $\rightarrow$ `TransferSession` $\rightarrow$ `FileManager`.
   - Áp dụng kỹ thuật trích xuất dữ liệu (Snapshot) trước khi gọi phương thức khác (không lồng khóa).
   - Quản lý hạn ngạch đĩa lưu trữ (10 GiB Quota) bằng giải thuật **CAS không khóa (`AtomicLong.compareAndSet`)**.
5. **Streaming Hash SHA-256 tức thì:**
   - Tính toán băm lũy tiến song song theo từng chunk 64 KiB khi dữ liệu vừa chạm RAM. Bước hoàn tất (Commit) diễn ra **tức thì ngay trong bộ nhớ RAM**, không tốn lượt đọc lại đĩa cứng, loại bỏ nguy cơ quá hạn Socket Timeout.
6. **Khôi phục khi đứt mạng (Checkpoint & Resume):**
   - Vị trí tiếp tục (`nextOffset`) được đối chiếu trực tiếp từ con trỏ đĩa thực tế của server.
   - Bảo mật phiên truyền bằng mã ngẫu nhiên 128-bit `resumeToken` chống tấn công chiếm đoạt lượt truyền.
7. **Tự động tìm kiếm Server qua UDP Broadcast (Cổng 5001):**
   - Client tự động quét và kết nối tới server trong mạng LAN, người dùng không cần gõ IP thủ công.
8. **Mô hình 2 luồng phía Client:**
   - Tách rời luồng giao diện bàn phím (`ConsoleUI`) và luồng mạng chạy ngầm (`TransferClient`), cho phép người dùng tạm dừng (`pause`), tiếp tục (`resume`), hoặc hủy (`cancel`) mà không bị đơ màn hình.

---

## 2. CẤU TRÚC THƯ MỤC DỰ ÁN

```text
codeV2/
├── src/
│   └── lftp/
│       ├── common/                     # Các lớp dùng chung giữa Client & Server
│       │   ├── Protocol.java           # Định nghĩa hằng số, Opcode, Status, Limits, Timeouts
│       │   ├── Message.java            # Mô hình thực thể thông điệp nhị phân
│       │   ├── MessageIO.java          # Bộ đọc/ghi khung nhị phân 12-byte header an toàn
│       │   ├── HashUtils.java          # Tiện ích tính băm SHA-256 toàn bộ file và lũy tiến
│       │   ├── FileInfo.java           # Đối tượng siêu dữ liệu file hoàn tất
│       │   └── RequestException.java   # Ngoại lệ giao thức kèm mã trạng thái
│       ├── server/                     # Toàn bộ mã nguồn phía Máy chủ (Server)
│       │   ├── ServerConfig.java       # Quản lý cấu hình cổng, thư mục, quota
│       │   ├── FileStorage.java        # Tầng I/O đĩa: file .tmp, đổi tên nguyên tử, dọn rác
│       │   ├── FileManager.java        # Danh mục file hoàn tất và Quota CAS không khóa
│       │   ├── TransferSession.java    # Trạng thái phiên truyền dở và MessageDigest RAM
│       │   ├── SessionManager.java     # Bảng session, cấp phát resumeToken, thu hồi sau 30p
│       │   ├── UploadManager.java      # Ghi chunk đĩa và băm SHA-256 lũy tiến
│       │   ├── DownloadManager.java    # Đọc chunk 64 KiB từ file hoàn tất
│       │   ├── ClientHandler.java      # State machine phục vụ 1 client trên worker thread
│       │   ├── ConnectionListener.java # Lắng nghe TCP 5000 và quản lý ThreadPool 20 luồng
│       │   ├── ConnectionWatcher.java  # Luồng quét ngắt socket đơ (60s) và dọn session quá hạn
│       │   ├── DiscoveryServer.java    # Phản hồi tìm kiếm UDP Broadcast 5001
│       │   └── ServerMain.java         # Điểm khởi chạy chính của Server
│       └── client/                     # Toàn bộ mã nguồn phía Ứng dụng khách (Client)
│           ├── ClientConfig.java       # Cấu hình client, thư mục state, downloads
│           ├── TransferProgress.java   # Tính tốc độ MB/s trung bình trượt, %, ETA, vẽ thanh tiến độ
│           ├── ResumeManager.java      # Lưu trữ trạng thái truyền dở vào .client-state trên đĩa
│           ├── DiscoveryClient.java    # Phát gói UDP Broadcast 255.255.255.255 để tìm IP server
│           ├── TransferClient.java     # Luồng công nhân mạng (Luồng 2): upload, download, resume
│           ├── ConsoleUI.java          # Luồng giao diện bàn phím (Luồng 1): menu, gõ lệnh, can thiệp
│           └── ClientMain.java         # Điểm khởi chạy chính của Client
├── bin/                                # Thư mục chứa các tệp .class sau khi biên dịch
├── compile.bat                         # Kịch bản biên dịch toàn bộ dự án trên Windows
├── run-server.bat                      # Kịch bản khởi chạy nhanh Server
├── run-client.bat                      # Kịch bản khởi chạy nhanh Client
├── Dockerfile                          # Đóng gói Server vào Docker container
├── docker-compose.yml                  # Khởi chạy Server chế độ host network với Docker Compose
└── README.md                           # Tài liệu hướng dẫn sử dụng
```

---

## 3. HƯỚNG DẪN BIÊN DỊCH VÀ KHỞI CHẠY

Yêu cầu môi trường: **Java SE Development Kit (JDK) 17 trở lên**.

### Cách 1: Sử dụng kịch bản Batch trên Windows (Khuyên dùng)
1. **Biên dịch:** Bấm đúp vào tệp `compile.bat` (hoặc chạy trong cmd: `compile.bat`).
2. **Chạy Server:** Bấm đúp vào tệp `run-server.bat`.
   * Server sẽ khởi động, dọn dẹp các file `.tmp` cũ nếu có, và bắt đầu lắng nghe TCP cổng 5000, UDP cổng 5001.
3. **Chạy Client:** Bấm đúp vào tệp `run-client.bat` (có thể mở nhiều cửa sổ client cùng lúc để test đa luồng).
   * Client sẽ tự động phát hiện server qua UDP Broadcast và kết nối tức thì!

### Cách 2: Biên dịch và chạy bằng dòng lệnh thủ công
```bash
# Di chuyển vào thư mục codeV2
cd /d "d:\project\BTL LTM\codeV2"

# Tạo thư mục bin nếu chưa có
mkdir bin

# Biên dịch toàn bộ mã nguồn Java
javac -encoding UTF-8 -d bin src/lftp/common/*.java src/lftp/server/*.java src/lftp/client/*.java

# Khởi chạy Server
java -cp bin lftp.server.ServerMain

# Khởi chạy Client (ở một terminal khác)
java -cp bin lftp.client.ClientMain
```

### Cách 3: Chạy bằng Docker & Docker Compose
```bash
# Chạy Server trong Docker container với network host
docker-compose up -d

# Xem log hoạt động của server
docker-compose logs -f
```

---

## 4. HƯỚNG DẪN SỬ DỤNG GIAO DIỆN CLIENT CLI

Khi ứng dụng Client khởi động, giao diện dòng lệnh tương tác sẽ xuất hiện:

```text
===============================================================
  LFTP/1 CLIENT - ỨNG DỤNG TRUYỀN TỆP MẠNG NỘI BỘ (LAN) CLI    
  Nhóm TKD: Nguyễn Trung Tường - Bùi Văn Đạt - Nguyễn Đình Khải 
===============================================================
LFTP> 
```

### Danh sách các lệnh điều khiển:

| Lệnh | Cú pháp ví dụ | Mô tả chức năng |
|---|---|---|
| `discover` | `discover` | Tự động quét tìm máy chủ trong mạng LAN qua UDP Broadcast |
| `connect` | `connect 192.168.1.100 5000` | Kết nối thủ công tới máy chủ theo IP và cổng |
| `list` | `list 0 10` | Xem danh mục tệp hoàn tất trên server (Trang 0, mỗi trang 10 tệp) |
| `upload` | `upload D:/data/video.mp4` | Tải lên tệp (Tự động tính SHA-256 nguồn, tự động Resume nếu rớt mạng) |
| `download` | `download file-abc12345 ./downloads` | Tải tệp từ server về máy (Tự động xác minh SHA-256 tải về) |
| `pause` | `pause` | Tạm dừng lượt truyền đang chạy |
| `resume` | `resume` | Tiếp tục lượt truyền vừa tạm dừng |
| `cancel` | `cancel` | Hủy bỏ lượt truyền hiện tại |
| `pending` | `pending` | Xem danh sách các phiên tải lên bị gián đoạn đã lưu trên đĩa |
| `status` | `status` | Xem trạng thái kết nối mạng và tiến trình truyền hiện tại |
| `help` | `help` | Hiển thị bảng trợ giúp danh mục lệnh |
| `exit` | `exit` | Đóng kết nối an toàn và thoát ứng dụng |

---

## 5. KỊCH BẢN KIỂM THỬ THỰC TẾ (DEMO KHI BẢO VỆ)

1. **Test UDP Auto-Discovery:**
   - Mở Server trước.
   - Mở Client, thấy thông báo *"Đã tìm thấy Server: TKD-Server tại IP: 127.0.0.1 (Cổng TCP: 5000)"* và tự động kết nối thành công mà không cần nhập IP.
2. **Test Upload & Streaming Hash:**
   - Gõ `upload <tệp_lớn>`. Quan sát thanh tiến độ thời gian thực: phần trăm, tốc độ (MB/s), thời gian còn lại (ETA).
   - Khi đạt 100%, server đối chiếu SHA-256 tức thì từ RAM và báo thành công ngay lập tức.
3. **Test Resume khi mất kết nối:**
   - Khi đang upload được khoảng 40%, rút dây mạng hoặc đóng cửa sổ Client (hoặc gõ `cancel` / ngắt socket).
   - Mở lại Client, gõ lại lệnh `upload <tệp_đó>`, Client tự động gửi `UPLOAD_RESUME`, Server đồng ý và truyền tiếp ngay từ byte 40% mà không phải truyền lại từ đầu!
4. **Test Concurrency:**
   - Mở đồng thời 3–5 cửa sổ Client, cùng tải file lên và tải file về song song. Cả 5 client đều hoạt động mượt mà, không xảy ra xung đột hay Deadlock.

