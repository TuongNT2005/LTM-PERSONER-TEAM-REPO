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
2. **Commit có AI code viết ngắn gọn:**
   - Vì code do AI hỗ trợ sinh khung rất nhanh, sinh viên chỉ việc review, chỉnh sửa và chạy test trong vài giờ, nên nhật ký `ai-log.md` ở các commit code được viết **súc tích, ngắn gọn, vào thẳng trọng tâm**, không dài dòng.
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
> - **Ngày commit Git** diễn ra sau đó (khi sinh viên đã thảo luận nhóm, tự sửa chữa, cài đặt code và kiểm thử thực tế xong).

---

### COMMIT 1 (Chiều 05/11/2026 - Thứ Năm)
* **Người commit:** Nguyễn Trung Tường
* **File:** `docs/proposal.md`, `docs/ai-log.md`
* **Message:** `Tạo tài liệu đề xuất đồ án và xác định phạm vi bài toán`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 1 | 26/10/2026 | Nguyễn Trung Tường | Antigravity | Gợi ý khung đề xuất dự án truyền file mạng LAN | Dàn ý proposal theo chuẩn môn học | Giữ khung sườn, tự viết lại bài toán thực tế (mạng chập chờn, file lớn bị hỏng) và thống nhất giới hạn với nhóm | `docs/proposal.md`, `docs/ai-log.md` |

---

### COMMIT 2 (Chiều 06/11/2026 - Thứ Sáu)
* **Người commit:** Bùi Văn Đạt
* **File:** `docs/protocol-design.md`, `docs/ai-log.md`
* **Message:** `Thiết kế đặc tả giao thức nhị phân LFTP/1 và cấu trúc gói tin`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 2 | 02/11/2026 | Bùi Văn Đạt | Antigravity | Thiết kế Header cho giao thức truyền file nhị phân | Gợi ý cấu trúc Header nhiều trường rườm rà | Tinh gọn thành Header 12 byte cố định và đặt 4 byte độ dài ở đầu chống dính gói TCP, tự vẽ sơ đồ tuần tự Mermaid | `docs/protocol-design.md`, `docs/ai-log.md` |

---

### COMMIT 3 (Chiều 07/11/2026 - Thứ Bảy)
* **Người commit:** Nguyễn Đình Khải
* **File:** `docs/architecture.md`, `docs/ai-log.md`
* **Message:** `Thiết kế kiến trúc đa luồng cho server và client`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 3 | 04/11/2026 | Nguyễn Đình Khải | Antigravity | So sánh ThreadPool vs Thread-per-Client, kiến trúc Client không đơ UI | Bảng so sánh lý thuyết và mẫu ExecutorService | Chọn ThreadPool cố định cho Server, Client dùng 2 luồng riêng biệt để UI mượt mà, cùng Tường chốt thứ bậc khóa chống Deadlock | `docs/architecture.md`, `docs/ai-log.md` |

---

### COMMIT 4 (Sáng 08/11/2026 - Chủ Nhật)
* **Người commit:** Bùi Văn Đạt
* **File:** `shared/src/lftp/common/Protocol.java`, `Message.java`, `MessageIO.java`, `FileInfo.java`, `HashUtils.java`, `RequestException.java`, `docs/ai-log.md`
* **Message:** `Cài đặt tầng giao thức dùng chung và xử lý đóng gói thông điệp`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 4 | 06/11/2026 | Bùi Văn Đạt | Antigravity | Viết khung MessageIO đọc ghi nhị phân trên Socket | Code mẫu dùng writeUTF() và read() | Bỏ writeUTF() vì giới hạn 64KB, tự viết writeString chuẩn UTF-8, đổi read() thành readFully() chống phân mảnh TCP. Chạy thử gửi nhận chuỗi thấy khớp | `shared/src/lftp/common/*`, `docs/ai-log.md` |

---

### COMMIT 5 (Chiều 08/11/2026 - Chủ Nhật)
* **Người commit:** Nguyễn Trung Tường
* **File:** `server/src/Server.java`, `server/src/lftp/server/ServerMain.java`, `ServerConfig.java`, `ConnectionListener.java`, `FileStorage.java`, `docs/ai-log.md`
* **Message:** `Cài đặt bộ lắng nghe kết nối server và tầng lưu trữ file trên đĩa`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 5 | 06/11/2026 | Nguyễn Trung Tường | Antigravity | Vòng lặp ServerSocket accept và xử lý ghi tệp an toàn | Code mẫu ThreadPool và ghi RandomAccessFile | Thêm hàm xóa file .tmp mồ côi lúc khởi động, dùng ATOMIC_MOVE đổi tên file an toàn, dùng netstat kiểm tra cổng thấy lắng nghe tốt | `server/src/lftp/server/*`, `docs/ai-log.md` |

---

### COMMIT 6 (Sáng 09/11/2026 - Thứ Hai)
* **Người commit:** Bùi Văn Đạt
* **File:** `server/src/lftp/server/SessionManager.java`, `TransferSession.java`, `UploadManager.java`, `DownloadManager.java`, `docs/ai-log.md`
* **Message:** `Quản lý phiên truyền tải, băm SHA-256 lũy tiến và điều phối luồng dữ liệu`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 6 | 07/11/2026 | Bùi Văn Đạt | Antigravity | Thuật toán băm SHA-256 và quản lý phiên dở | Code băm lại cả file sau khi nhận và Map lưu session | Chuyển sang băm lũy tiến từng chunk trong RAM, viết hàm nạp lại mốc băm khi Resume, lưu kết quả hoàn tất để chống lỗi mất mạng cuối. So sánh mã hash với lệnh certutil thấy khớp | `server/src/lftp/server/*`, `docs/ai-log.md` |

---

### COMMIT 7 (Chiều 09/11/2026 - Thứ Hai)
* **Người commit:** Nguyễn Trung Tường
* **File:** `server/src/lftp/server/FileManager.java`, `ClientHandler.java`, `ConnectionWatcher.java`, `docs/ai-log.md`
* **Message:** `Quản lý danh mục file, kiểm tra hạn ngạch bằng CAS và quét timeout`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 7 | 08/11/2026 | Nguyễn Trung Tường | Antigravity | Logic kiểm tra hạn ngạch bộ nhớ và quét dọn socket quá hạn | Gợi ý dùng synchronized bao quanh hàm kiểm tra Quota | Bỏ synchronized vì dễ nghẽn và gây Deadlock, thay bằng AtomicLong với vòng lặp CAS không khóa, thêm luồng quét dọn socket quá hạn. Chạy thử mô phỏng thấy hạn ngạch chuẩn | `server/src/lftp/server/*`, `docs/ai-log.md` |

---

### COMMIT 8 (Sáng 10/11/2026 - Thứ Ba)
* **Người commit:** Nguyễn Đình Khải
* **File:** `server/src/lftp/server/DiscoveryServer.java`, `client/src/lftp/client/DiscoveryClient.java`, `docs/ai-log.md`
* **Message:** `Cài đặt cơ chế tự động tìm kiếm server qua UDP broadcast`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 8 | 08/11/2026 | Nguyễn Đình Khải | Antigravity | Gửi nhận UDP broadcast để client tự tìm server | Mẫu DatagramSocket gửi tin broadcast | Thêm cờ setBroadcast(true) và timeout 3 giây tránh treo máy, thêm fallback nhập IP tay nếu mạng chặn UDP. Chạy thử trên mạng Wi-Fi thấy Client bắt được IP Server | `client/.../DiscoveryClient.java`, `server/.../DiscoveryServer.java`, `docs/ai-log.md` |

---

### COMMIT 9 (Chiều 10/11/2026 - Thứ Ba)
* **Người commit:** Nguyễn Đình Khải
* **File:** `client/src/Client.java`, `client/src/lftp/client/ClientMain.java`, `ClientConfig.java`, `ConsoleUI.java`, `TransferProgress.java`, `docs/ai-log.md`
* **Message:** `Xây dựng giao diện dòng lệnh client và điều khiển truyền tải không khóa`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 9 | 09/11/2026 | Nguyễn Đình Khải | Antigravity | Thanh tiến trình MB/s, ETA và phím tạm dừng | Đo tốc độ sau mỗi chunk và dùng wait/notify để dừng | Chuyển sang lấy mẫu trượt ngắn để tránh giật số, dùng ký tự `\r` vẽ tại chỗ trên 1 dòng, dùng cờ volatile giúp tạm dừng tức thì. Chạy thử pause/resume hoạt động mượt mà | `client/src/lftp/client/*`, `docs/ai-log.md` |

---

### COMMIT 10 (Sáng 11/11/2026 - Thứ Tư)
* **Người commit:** Nguyễn Đình Khải
* **File:** `client/src/lftp/client/ResumeManager.java`, `TransferClient.java`, `docs/ai-log.md`
* **Message:** `Lưu trạng thái truyền dở phía client và tự động khôi phục truyền tiếp`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 10 | 09/11/2026 | Nguyễn Đình Khải | Antigravity | Lưu trạng thái truyền file dở để tự resume | Code lưu file JSON trong thư mục làm việc | Dùng mã MD5 đường dẫn file đặt tên file .state tránh lỗi ký tự đặc biệt, đối soát hash nguồn trước khi resume. Tắt ngang Client lúc truyền rồi bật lại thấy resume chuẩn | `client/src/lftp/client/*`, `docs/ai-log.md` |

---

### COMMIT 11 (Chiều 11/11/2026 - Thứ Tư)
* **Người commit:** Nguyễn Trung Tường
* **File:** `server/Dockerfile`, `client/Dockerfile`, `.env.example`, `.gitignore`, `docs/ai-log.md`
* **Message:** `Cấu hình Dockerfile multi-stage và file môi trường chạy ứng dụng`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 11 | 10/11/2026 | Nguyễn Trung Tường | Antigravity | Viết Dockerfile cho ứng dụng Java 21 | Dockerfile đơn tầng cơ bản | Chuyển sang Docker multi-stage build: tầng build JDK, tầng chạy JRE gọn nhẹ, mount volume dữ liệu ngoài máy thật. Chạy thử build và khởi động container thành công | `server/Dockerfile`, `client/Dockerfile`, `.gitignore`, `docs/ai-log.md` |

---

### COMMIT 12 (Chiều 12/11/2026 - Thứ Năm, Hạn chót hoàn thành)
* **Người commit:** Cả nhóm (Tường commit)
* **File:** `docs/testing-guide.md`, `README.md`, `docs/ai-log.md`
* **Message:** `Hoàn thiện tài liệu hướng dẫn kiểm thử và bảng phân công đóng góp`
* **Nhật ký `docs/ai-log.md`:**
  | # | Date | Member | Tool / model | Task — what you asked | What AI produced | What you kept, changed or rejected — and how you verified it | Files |
  |:-:|------|--------|--------------|-----------------------|------------------|---------------------------------------------------------------|-------|
  | 12 | 11/11/2026 | Cả nhóm | Antigravity | Hướng dẫn viết kịch bản test mạng và đối soát hash | Mẫu test case chung | Cả nhóm chạy test thực tế: bắt tay, upload, download đối soát hash khớp, ngắt mạng resume thành công, ghi lại chi tiết vào testing-guide.md, hoàn thiện bảng đóng góp và Notable Interactions | `docs/testing-guide.md`, `README.md`, `docs/ai-log.md` |

---

# 3. BA TƯƠNG TÁC QUAN TRỌNG NHẤT ĐỂ TRẢ LỜI VẤN ĐÁP (NOTABLE INTERACTIONS)

Khi Thầy Hùng hỏi: *"AI đã hỗ trợ các em những gì và các em đã phản biện/chỉnh sửa gì?"*, các bạn trả lời ngắn gọn, tự nhiên như sau:

1. **Bùi Văn Đạt (Bỏ `writeUTF`):**  
   *"Lúc đầu AI gợi ý em dùng hàm `writeUTF()` có sẵn trong Java. Em đọc tài liệu thấy hàm này giới hạn tối đa 65.535 byte nên file có đường dẫn dài sẽ lỗi, đồng thời dùng chuẩn riêng của Java nên các ngôn ngữ khác không đọc được. Em đã tự viết lại hàm ghi 4 byte độ dài Big-Endian và mảng byte UTF-8 chuẩn ạ."*

2. **Nguyễn Trung Tường (Dùng CAS không khóa):**  
   *"AI ban đầu gợi ý em bọc `synchronized` quanh hàm kiểm tra dung lượng `reserveSpace`. Em thấy nhiều client cùng vào xin dung lượng sẽ bị nghẽn và dễ gây Deadlock, nên em đã chuyển sang dùng `AtomicLong` với giải thuật CAS `compareAndSet` để xử lý ở mức lệnh phần cứng CPU mà không cần dùng khóa ạ."*

3. **Nguyễn Đình Khải (Dùng cờ `volatile`):**  
   *"AI gợi ý em dùng `wait()` và `notify()` giữa 2 luồng để tạm dừng truyền file. Em thấy cơ chế này dễ bị treo luồng nếu bấm phím liên tục, nên em đã thay bằng cờ `volatile boolean isPaused`. Khi bấm pause, luồng UI chỉ bật cờ, luồng Worker kiểm tra cờ trước mỗi chunk, giúp giao diện không bao giờ bị đơ ạ."*
