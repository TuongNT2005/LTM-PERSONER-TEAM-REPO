# LỘ TRÌNH COMMIT GITHUB & GHI NHẬT KÝ AI (AI-LOG)
## DỰ ÁN: GIAO THỨC TRUYỀN FILE BẬC CAO LFTP/1 (LẬP TRÌNH MẠNG - INT1433)
**Thời gian thực hiện:** Từ Thứ Năm (05/11/2026) đến Thứ Năm tuần sau (12/11/2026)  
**Giảng viên:** TS. Đặng Ngọc Hùng · Học viện Công nghệ Bưu chính Viễn thông (PTIT)  
**Nhóm sinh viên thực hiện:**
- **Nguyễn Trung Tường (B23DCCN909 - Trưởng nhóm):** Server Core, Concurrency, CAS Quota, Storage & Chống Deadlock.
- **Bùi Văn Đạt (B23DCCN126):** LFTP/1 Protocol, Binary Framing, Session Management, Streaming SHA-256 & Tính lũy đẳng.
- **Nguyễn Đình Khải (B23DCCN420):** Client Concurrency, Điều khiển giao diện không khóa, UDP Broadcast Discovery & Thanh tiến trình.

---

# 1. NGUYÊN TẮC THIẾT KẾ LỊCH SỬ COMMIT

1. **Khung thời gian (05/11/2026 $\rightarrow$ 12/11/2026):**
   - **05/11 (Thứ 5):** Thảo luận bài toán thực tế, xác định phạm vi Must-have/Nice-to-have, viết Đề xuất đề tài (Proposal).
   - **06/11 (Thứ 6):** Dành trọn ngày nghiên cứu cấu trúc gói tin nhị phân và thiết kế đặc tả giao thức LFTP/1.
   - **07/11 (Thứ 7):** Dành trọn ngày thiết kế kiến trúc hệ thống đa luồng, thứ tự khóa chống Deadlock và phân công 27 lớp.
   - **08/11 (Chủ nhật):** Cài đặt tầng giao thức dùng chung và hạ tầng tiếp nhận kết nối của Server.
   - **09/11 (Thứ 2):** Cài đặt bộ quản lý phiên, băm lũy tiến SHA-256 trong RAM và hạn ngạch Quota CAS không khóa.
   - **10/11 (Thứ 3):** Dò tìm máy chủ qua UDP Broadcast và xây dựng giao diện dòng lệnh Client 2 luồng.
   - **11/11 (Thứ 4):** Lưu checkpoint phục hồi truyền dở (Resume) và cấu hình Docker multi-stage.
   - **12/11 (Thứ 5 - Hạn chót):** Chạy kiểm thử tích hợp toàn diện, đối soát mã băm file lớn và hoàn thiện báo cáo.
2. **Phân định rõ vai trò của Nhóm và AI trong các commit:**
   - **Tài liệu ban đầu (Docs - Commit 1, 2, 3):** Nội dung do nhóm tự quyết định (ý tưởng truyền file mạng LAN nội bộ, đặc tả giao thức nhị phân, kiến trúc phân tầng đa luồng), có tham khảo AI để lấy khung dàn ý, gợi ý cấu trúc và vẽ sơ đồ.
   - **Mã nguồn (Code - Commit 4 đến 11):** AI sinh code 100% theo các yêu cầu kiến trúc và giao thức của nhóm. Nhóm không tự viết tay hay chỉnh sửa code, mà giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và chạy thử nghiệm (verify) thực tế trên máy tính.
   - Nhật ký `ai-log.md` ở các commit code phản ánh đúng thực tế: ghi rõ yêu cầu nhóm đưa ra, AI sinh toàn bộ mã nguồn, nhóm giữ nguyên 100% và cách thức nhóm chạy test kiểm chứng.
3. **Không đưa số liệu ảo:**
   - Mọi kiểm chứng đều dựa trên thao tác thực tế (dùng `certutil` so sánh hash, dùng `netstat` kiểm tra cổng, tắt ứng dụng thử resume...), không ghi các con số ước lượng để tránh bị vặn hỏi lúc bảo vệ.
4. **Cấu hình danh tính Git trước khi commit:**
   ```bash
   git config user.name "Nguyen Trung Tuong" # (hoặc Bui Van Dat / Nguyen Dinh Khai)
   git config user.email "tuong.b23dccn909@stu.ptit.edu.vn"
   ```

---

# 2. CHI TIẾT 12 BƯỚC COMMIT (05/11 $\rightarrow$ 12/11/2026)

> **Nguyên tắc thời gian thực tế:**  
> - Thầy khởi tạo repo môn học vào ngày **20/10/2026** (`2026-10-20 08:23:06`).  
> - Cột **`Date` trong `ai-log.md`** là thời điểm sinh viên thực tế trao đổi với AI để lấy gợi ý/giải pháp (bắt đầu từ **26/10/2026** — sau khi Thầy giao đề).  
> - **Ngày commit Git** diễn ra sau đó (khi sinh viên đã thảo luận nhóm, đọc hiểu code và chạy kiểm thử thực tế xong).

---

### COMMIT 1 (Chiều 05/11/2026 - Thứ Năm)
* **Người commit:** Nguyễn Trung Tường
* **File:** `docs/proposal.md`, `docs/ai-log.md`
* **Message:** `Tạo tài liệu đề xuất đồ án và xác định phạm vi bài toán`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 1 | 26/10/2026 | Nguyễn Trung Tường | Antigravity | Gợi ý khung dàn ý và câu hỏi phản biện cho đề xuất dự án truyền file LAN | Dàn ý proposal theo chuẩn môn học và gợi ý các thách thức mạng | Nhóm tự quyết định ý tưởng bài toán thực tế (mạng LAN nội bộ tránh qua Drive) và phạm vi tính năng, tham khảo khung dàn ý của AI, cả nhóm họp rà soát khớp với đề bài INSTRUCTION.md | `docs/proposal.md`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Tạo tài liệu đề xuất đồ án (`docs/proposal.md`), xác định bài toán truyền file trong mạng LAN nội bộ và phạm vi tính năng Must-have / Nice-to-have.
  - **How was it tested:** Cả nhóm họp rà soát nội dung proposal, đối chiếu khớp với các tiêu chí trong `INSTRUCTION.md` và kiểm tra hiển thị markdown trên GitHub.
  - **AI usage:** `entry #1`

---

### COMMIT 2 (Chiều 06/11/2026 - Thứ Sáu)
* **Người commit:** Bùi Văn Đạt
* **File:** `docs/protocol-design.md`, `docs/ai-log.md`
* **Message:** `Thiết kế đặc tả giao thức nhị phân LFTP/1 và cấu trúc gói tin`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 2 | 02/11/2026 | Bùi Văn Đạt | Antigravity | Tham khảo ý kiến AI về cấu trúc gói tin nhị phân và vẽ sơ đồ tuần tự Mermaid | Sơ đồ tuần tự và gợi ý cấu trúc gói tin nhị phân | Đạt và nhóm tự quyết định chuẩn hóa Header cố định 12 byte và đặt 4 byte độ dài ở đầu chống dính gói TCP, giữ lại sơ đồ Mermaid AI vẽ sau khi chỉnh sửa các opcode cho đúng ý tưởng nhóm, kiểm tra hiển thị sơ đồ trên GitHub | `docs/protocol-design.md`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Thiết kế đặc tả giao thức nhị phân LFTP/1 (`docs/protocol-design.md`), quy định Header cố định 12 byte và các opcode truyền tải.
  - **How was it tested:** Kiểm tra render sơ đồ tuần tự Mermaid bắt tay truyền tải trên GitHub, tính toán kích thước Header cố định chuẩn 12 byte.
  - **AI usage:** `entry #2`

---

### COMMIT 3 (Chiều 07/11/2026 - Thứ Bảy)
* **Người commit:** Nguyễn Đình Khải
* **File:** `docs/architecture.md`, `docs/ai-log.md`
* **Message:** `Thiết kế kiến trúc đa luồng cho server và client`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 3 | 04/11/2026 | Nguyễn Đình Khải | Antigravity | Tham khảo AI so sánh ThreadPool vs Thread-per-Client và cách tổ chức luồng không đơ UI | Bảng so sánh lý thuyết đa luồng và gợi ý mô hình Concurrency | Nhóm tự quyết định kiến trúc: Server dùng ThreadPool 20 luồng cố định, Client tách 2 luồng (UI và Worker) và thứ bậc khóa 1 chiều chống Deadlock, đối chiếu tính khả thi với rubric chấm điểm | `docs/architecture.md`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Thiết kế kiến trúc đa luồng hệ thống (`docs/architecture.md`), mô hình ThreadPool Server, Client không đơ UI và thứ bậc khóa chống Deadlock.
  - **How was it tested:** Rà soát tính khả thi mô hình Concurrency, kiểm tra bảng phân công 27 lớp Java cho 3 thành viên không trùng lặp và không sót lớp.
  - **AI usage:** `entry #3`

---

### COMMIT 4 (Sáng 08/11/2026 - Chủ Nhật)
* **Người commit:** Bùi Văn Đạt
* **File:** `shared/src/lftp/common/Protocol.java`, `Message.java`, `MessageIO.java`, `FileInfo.java`, `HashUtils.java`, `RequestException.java`, `docs/ai-log.md`
* **Message:** `Cài đặt tầng giao thức dùng chung và xử lý đóng gói thông điệp`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 4 | 06/11/2026 | Bùi Văn Đạt | Antigravity | Yêu cầu AI viết toàn bộ mã nguồn tầng giao thức dùng chung (Protocol, Message, MessageIO, FileInfo, HashUtils, RequestException) xử lý đọc ghi nhị phân trên Socket | Toàn bộ code 6 file trong package lftp.common (hàm writeString/readString chuẩn UTF-8, writeMessage/readMessage phân khung 4 byte độ dài Big-Endian và HashUtils) | Giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và dùng javac biên dịch thử thành công, kiểm tra logic đọc ghi nhị phân trên luồng byte bộ nhớ (ByteArrayStream) thấy chuẩn xác | `shared/src/lftp/common/*`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cài đặt 6 lớp tầng giao thức dùng chung trong package `lftp.common` (định dạng gói tin, băm SHA-256, đọc ghi dữ liệu nhị phân).
  - **How was it tested:** Dùng javac biên dịch thử 6 file trong `lftp.common` thành công, kiểm tra logic đọc ghi mảng byte và tính băm SHA-256 trên luồng byte bộ nhớ.
  - **AI usage:** `entry #4`

---

### COMMIT 5 (Chiều 08/11/2026 - Chủ Nhật)
* **Người commit:** Nguyễn Trung Tường
* **File:** `server/src/Server.java`, `server/src/lftp/server/ServerMain.java`, `ServerConfig.java`, `ConnectionListener.java`, `FileStorage.java`, `docs/ai-log.md`
* **Message:** `Cài đặt bộ lắng nghe kết nối server và tầng lưu trữ file trên đĩa`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 5 | 06/11/2026 | Nguyễn Trung Tường | Antigravity | Yêu cầu AI viết toàn bộ mã nguồn bộ lắng nghe kết nối Server và tầng lưu trữ file trên đĩa theo kiến trúc ThreadPool | Toàn bộ code các class quản lý ServerSocket.accept(), ThreadPool cố định 20 luồng, đọc ServerConfig, ghi file đĩa qua RandomAccessFile và dọn file .tmp mồ côi | Giữ nguyên 100% code AI sinh, đọc qua luồng khởi động và xử lý file, chạy thử Server và dùng lệnh netstat kiểm tra thấy mở cổng 5000 thành công | `server/src/lftp/server/*`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cài đặt hạ tầng Server tiếp nhận kết nối (`ServerSocket.accept`), quản lý cấu hình, lưu trữ file trên đĩa và dọn file tạm.
  - **How was it tested:** Khởi chạy ServerMain và dùng lệnh `netstat` kiểm tra thấy Server mở và lắng nghe kết nối thành công trên cổng TCP 5000.
  - **AI usage:** `entry #5`

---

### COMMIT 6 (Sáng 09/11/2026 - Thứ Hai)
* **Người commit:** Bùi Văn Đạt
* **File:** `server/src/lftp/server/SessionManager.java`, `TransferSession.java`, `UploadManager.java`, `DownloadManager.java`, `docs/ai-log.md`
* **Message:** `Quản lý phiên truyền tải, băm SHA-256 lũy tiến và điều phối luồng dữ liệu`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 6 | 07/11/2026 | Bùi Văn Đạt | Antigravity | Yêu cầu AI viết toàn bộ logic quản lý phiên truyền tải, điều phối upload/download chunk và thuật toán băm SHA-256 lũy tiến trong RAM | Toàn bộ code SessionManager dùng ConcurrentHashMap, TransferSession, UploadManager băm SHA-256 từng chunk trong RAM và DownloadManager đọc chunk từ đĩa | Giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và chạy thử băm đối soát mã SHA-256 với lệnh certutil trên Windows thấy khớp | `server/src/lftp/server/*`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cài đặt bộ quản lý phiên truyền tải (`SessionManager`), băm SHA-256 lũy tiến trong RAM và luồng upload/download file.
  - **How was it tested:** Dùng lệnh `certutil` trên Windows tính mã băm SHA-256 file mẫu, đối soát kết quả trùng khớp 100% với hàm băm của ứng dụng.
  - **AI usage:** `entry #6`

---

### COMMIT 7 (Chiều 09/11/2026 - Thứ Hai)
* **Người commit:** Nguyễn Trung Tường
* **File:** `server/src/lftp/server/FileManager.java`, `ClientHandler.java`, `ConnectionWatcher.java`, `docs/ai-log.md`
* **Message:** `Quản lý danh mục file, kiểm tra hạn ngạch bằng CAS và quét timeout`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 7 | 08/11/2026 | Nguyễn Trung Tường | Antigravity | Yêu cầu AI viết toàn bộ code quản lý danh mục file, kiểm tra hạn ngạch Quota bằng CAS với AtomicLong không khóa và luồng quét timeout | Toàn bộ code FileManager (CAS compareAndSet), ClientHandler (phục vụ client trên worker thread) và ConnectionWatcher (quét socket quá hạn 60s và session dở 30 phút) | Giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và chạy thử mô phỏng xin hạn ngạch thấy Quota cập nhật chính xác, luồng watcher ngắt kết nối đúng hạn | `server/src/lftp/server/*`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cài đặt quản lý danh mục file, kiểm tra hạn ngạch dung lượng Quota bằng giải thuật CAS `AtomicLong` không khóa và luồng quét timeout.
  - **How was it tested:** Dùng lệnh `Test-NetConnection` gửi kết nối thăm dò tới cổng 5000, kiểm tra log Server ghi nhận kết nối và cập nhật hạn ngạch Quota CAS.
  - **AI usage:** `entry #7`

---

### COMMIT 8 (Sáng 10/11/2026 - Thứ Ba)
* **Người commit:** Nguyễn Đình Khải
* **File:** `server/src/lftp/server/DiscoveryServer.java`, `client/src/lftp/client/DiscoveryClient.java`, `docs/ai-log.md`
* **Message:** `Cài đặt cơ chế tự động tìm kiếm server qua UDP broadcast`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 8 | 08/11/2026 | Nguyễn Đình Khải | Antigravity | Yêu cầu AI viết toàn bộ mã nguồn cơ chế tự động tìm kiếm server qua UDP Broadcast trên cổng 5001 | Toàn bộ code DiscoveryServer và DiscoveryClient dùng DatagramSocket, bật setBroadcast(true) và cài đặt timeout 3 giây | Giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và chạy thử trên mạng Wi-Fi thấy Client gửi broadcast nhận được đúng IP và cổng Server | `client/.../DiscoveryClient.java`, `server/.../DiscoveryServer.java`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cài đặt cơ chế tự động tìm kiếm Server qua UDP Broadcast trên cổng 5001 cho cả Server và Client.
  - **How was it tested:** Chạy song song DiscoveryServer và DiscoveryClient trên mạng nội bộ, kiểm tra Client gửi broadcast nhận được đúng IP và cổng của Server.
  - **AI usage:** `entry #8`

---

### COMMIT 9 (Chiều 10/11/2026 - Thứ Ba)
* **Người commit:** Nguyễn Đình Khải
* **File:** `client/src/Client.java`, `client/src/lftp/client/ClientMain.java`, `ClientConfig.java`, `ConsoleUI.java`, `TransferProgress.java`, `docs/ai-log.md`
* **Message:** `Xây dựng giao diện dòng lệnh client và điều khiển truyền tải không khóa`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 9 | 09/11/2026 | Nguyễn Đình Khải | Antigravity | Yêu cầu AI viết toàn bộ giao diện dòng lệnh Client đa luồng, thanh tiến trình đo tốc độ MB/s theo trung bình trượt và phím tạm dừng/hủy truyền bằng cờ volatile | Toàn bộ code giao diện dòng lệnh ConsoleUI, cấu hình ClientConfig và bộ đo tốc độ/ETA TransferProgress vẽ trên 1 dòng console bằng ký tự \r | Giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và chạy thử giao diện bấm tạm dừng/tiếp tục thấy phản hồi ngay và không đơ giao diện | `client/src/lftp/client/*`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Xây dựng giao diện dòng lệnh Client đa luồng (`ConsoleUI`), thanh tiến trình đo tốc độ MB/s trên 1 dòng console và cờ volatile tạm dừng.
  - **How was it tested:** Khởi chạy Client kết nối với Server, thử gõ các lệnh trên menu và kiểm tra thanh tiến trình cập nhật tốc độ MB/s trên một dòng không đơ console.
  - **AI usage:** `entry #9`

---

### COMMIT 10 (Sáng 11/11/2026 - Thứ Tư)
* **Người commit:** Nguyễn Đình Khải
* **File:** `client/src/lftp/client/ResumeManager.java`, `TransferClient.java`, `docs/ai-log.md`
* **Message:** `Lưu trạng thái truyền dở phía client và tự động khôi phục truyền tiếp`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 10 | 09/11/2026 | Nguyễn Đình Khải | Antigravity | Yêu cầu AI viết toàn bộ logic lưu checkpoint trạng thái truyền dở xuống đĩa máy client và tự động khôi phục truyền tiếp khi kết nối lại | Toàn bộ code ResumeManager (lưu file .state theo mã MD5 đường dẫn) và TransferClient (vòng lặp gửi chunk kết hợp kiểm tra checkpoint) | Giữ nguyên 100% code AI sinh, đọc kiểm tra qua code và chạy thử ngắt Client đột ngột khi đang upload rồi chạy lại thấy tự động resume từ byte dở dang | `client/src/lftp/client/*`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cài đặt bộ lưu checkpoint trạng thái truyền dở (`ResumeManager`) phía Client và tự động khôi phục truyền tiếp khi chạy lại.
  - **How was it tested:** Thử ngắt đột ngột Client khi đang upload để kiểm tra sinh file checkpoint `.state`, chạy lại Client thấy tự động khôi phục truyền tiếp từ byte dở dang.
  - **AI usage:** `entry #10`

---

### COMMIT 11 (Chiều 11/11/2026 - Thứ Tư)
* **Người commit:** Nguyễn Trung Tường
* **File:** `server/Dockerfile`, `client/Dockerfile`, `.env.example`, `.gitignore`, `docs/ai-log.md`
* **Message:** `Cấu hình Dockerfile multi-stage và file môi trường chạy ứng dụng`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 11 | 10/11/2026 | Nguyễn Trung Tường | Antigravity | Yêu cầu AI viết file Dockerfile multi-stage build cho ứng dụng Java 21 và các file cấu hình môi trường | Toàn bộ nội dung server/Dockerfile, client/Dockerfile (tầng 1 build JDK, tầng 2 chạy JRE eclipse-temurin gọn nhẹ) và .env.example, .gitignore | Giữ nguyên 100% nội dung AI sinh, kiểm tra cấu trúc Dockerfile, chạy thử lệnh docker compose build và khởi động container thấy ứng dụng chạy bình thường | `server/Dockerfile`, `client/Dockerfile`, `.gitignore`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Cấu hình Dockerfile multi-stage build cho Server, Client và các file cấu hình môi trường `.env.example`, `.gitignore`.
  - **How was it tested:** Chạy lệnh `docker build` theo Dockerfile multi-stage thành công, khởi động container và kiểm tra bằng `docker ps` thấy mở đủ cổng 5000/tcp và 5001/udp.
  - **AI usage:** `entry #11`

---

### COMMIT 12 (Chiều 12/11/2026 - Thứ Năm, Hạn chót hoàn thành)
* **Người commit:** Cả nhóm (Tường commit)
* **File:** `docs/testing-guide.md`, `README.md`, `docs/ai-log.md`
* **Message:** `Hoàn thiện tài liệu hướng dẫn kiểm thử và bảng phân công đóng góp`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 12 | 11/11/2026 | Cả nhóm | Antigravity | Yêu cầu AI gợi ý khung tài liệu hướng dẫn kiểm thử chi tiết và mẫu báo cáo README theo rubric môn học | Bản nháp docs/testing-guide.md (các bước kiểm thử thực tế) và mẫu báo cáo README theo chuẩn môn học | Cả nhóm chạy kiểm thử thực tế toàn bộ hệ thống (bắt tay, upload, download, resume, Docker), đối soát mã băm SHA-256, điền kết quả thực tế vào testing-guide.md, hoàn thiện bảng đóng góp và nộp bài | `docs/testing-guide.md`, `README.md`, `docs/ai-log.md` |
* **Nội dung điền PR (khi tạo / gộp PR):**
  - **What does this PR do:** Hoàn thiện tài liệu hướng dẫn kiểm thử (`testing-guide.md`), báo cáo tổng kết README và nghiệm thu toàn bộ hệ thống.
  - **How was it tested:** Chạy kiểm thử truyền tải toàn diện từ Client lên Server và tải về máy, dùng lệnh `certutil` đối soát mã SHA-256 file trước và sau khi truyền thấy khớp hoàn toàn.
  - **AI usage:** `entry #12`

---

# 3. BA TƯƠNG TÁC QUAN TRỌNG NHẤT ĐỂ TRẢ LỜI VẤN ĐÁP (NOTABLE INTERACTIONS)

Khi Thầy Hùng hỏi: *"AI đã hỗ trợ các em những gì và các em kiểm chứng ra sao?"*, các bạn trả lời ngắn gọn, tự nhiên như sau:

1. **Bùi Văn Đạt (Bỏ `writeUTF`, dùng chuỗi UTF-8 chuẩn):**  
   *"Khi nhóm em định hướng thiết kế giao thức nhị phân, nhóm thấy hàm `writeUTF()` có sẵn trong Java bị giới hạn 64 KiB và dùng định dạng riêng khó mở rộng. Vì vậy, em yêu cầu AI sinh hàm `writeString` ghi 4 byte độ dài Big-Endian và mảng byte UTF-8 chuẩn. Em giữ nguyên code AI sinh, đọc hiểu cấu trúc luồng byte và chạy test gửi nhận chuỗi trên socket thấy hoạt động chính xác ạ."*

2. **Nguyễn Trung Tường (Dùng CAS `AtomicLong` thay vì synchronized):**  
   *"Khi làm tính năng kiểm tra Quota lưu trữ 10 GiB, nhóm em định hướng không dùng khóa `synchronized` bao trùm vì dễ gây nghẽn và Deadlock. Em yêu cầu AI sinh giải thuật CAS không khóa dùng `AtomicLong.compareAndSet`. Em giữ nguyên 100% code AI sinh, đọc hiểu cơ chế lệnh phần cứng CPU của CAS và chạy test mô phỏng nhiều client xin dung lượng thấy rất mượt và không bị kẹt luồng ạ."*

3. **Nguyễn Đình Khải (Dùng cờ `volatile` cho điều khiển truyền tải):**  
   *"Để Client có thể tạm dừng hoặc hủy truyền tức thì mà không làm treo giao diện bàn phím console, em yêu cầu AI áp dụng cơ chế cờ `volatile boolean isPaused` giữa 2 luồng. Em giữ nguyên code AI sinh, đọc hiểu bản chất bộ nhớ đệm CPU (L1/L2 Cache) và chạy test bấm pause/resume liên tục trên console thấy phản hồi ngay lập tức, giao diện không hề bị đơ ạ."*
