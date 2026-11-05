# HƯỚNG DẪN HỌC & BẢO VỆ ĐỒ ÁN CHO NGUYỄN TRUNG TƯỜNG (B23DCCN909)
## VAI TRÒ: TRƯỞNG NHÓM - PHỤ TRÁCH SERVER ARCHITECTURE, CONCURRENCY & STORAGE
**Môn học:** Lập trình mạng (INT1433) · PTIT  
**Giảng viên:** TS. Đặng Ngọc Hùng  

---

# 1. TỔNG QUAN PHẠM VI TRÁCH NHIỆM

Bạn là người chịu trách nhiệm về **"xương sống" của máy chủ (Server)**:
1. Tiếp nhận kết nối mạng đa luồng an toàn bằng `ThreadPoolExecutor` (khống chế tối đa 20 Worker Threads).
2. Tầng lưu trữ file vật lý trên đĩa (`FileStorage`), dọn dẹp file tạm mồ côi lúc khởi động (`cleanupTempFilesOnStartup`).
3. Quản lý danh mục tệp (`FileManager`) và hạn ngạch dung lượng (Quota 10 GiB) bằng giải thuật **CAS không khóa (`AtomicLong.compareAndSet`)**.
4. Quản lý thời gian chờ của kết nối (`ConnectionWatcher`) và đảm bảo hệ thống **triệt tiêu 100% Deadlock** thông qua thứ tự khóa một chiều.

---

# 2. CÁC TÀI LIỆU BẠN BẮT BUỘC PHẢI ĐỌC TRONG `TMP/`

1. **[`tmp/architectureV2.md`](file:///d:/project/BTL%20LTM/tmp/architectureV2.md):**
   - **Mục 1.1:** Mô hình Thread Pool có kiểm soát phía Server.
   - **Mục 2.1 & 2.2:** Sơ đồ Class Diagram phía Server và trách nhiệm của `ServerMain`, `ConnectionListener`, `ClientHandler`, `FileStorage`, `FileManager`, `ConnectionWatcher`.
   - **Mục 3:** Quy tắc lấy khóa 1 chiều (`Strict Lock Hierarchy`), nguyên tắc Snapshot nhả khóa ngay và cơ chế Quota không khóa bằng CAS.
   - **Mục 4:** Vòng đời kết nối (`Connection Lifecycle`).
2. **[`tmp/note.md`](file:///d:/project/BTL%20LTM/tmp/note.md):**
   - **Mục 1:** Phân tích Thread Stack và lỗi `OutOfMemoryError: unable to create new native thread`.
   - **Mục 3 & 4:** Bản chất phần cứng của CAS và vì sao CAS triệt tiêu được Deadlock.
   - **Mục 6:** Chứng minh toán học và luồng code triệt tiêu Deadlock (Lock Hierarchy proofs).
   - **Mục 7:** Phân biệt rạch ròi giữa `FileStorage` (Disk) và `FileManager` (RAM).
3. **[`tmp/key-word.md`](file:///d:/project/BTL%20LTM/tmp/key-word.md):**
   - Học thuộc các từ khóa của bạn: `ThreadPoolExecutor`, `CAS / Lock-free Quota`, `Strict Lock Hierarchy`, `Snapshot before Processing`, `Atomic Rename`, `Bootstrap Cleanup`, `Multi-layer Timeouts`.

---

# 3. CÁC CLASS MÃ NGUỒN BẠN PHẢI NẮM NHƯ LÒNG BÀN TAY

Tất cả code của bạn nằm trong package: [`server/src/lftp/server/`](file:///d:/project/BTL%20LTM/int1433-05-20261-grp-int1433-05-0017/server/src/lftp/server/)

### 3.1. `ConnectionListener.java` (Bộ tiếp nhận kết nối & Thread Pool)
- **Hàm `start()`:** Tạo `ServerSocket(port)` và khởi tạo:
  ```java
  this.workerPool = new ThreadPoolExecutor(
      config.getMaxClients(), config.getMaxClients(),
      60L, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(config.getMaxClients() * 2),
      new ThreadPoolExecutor.AbortPolicy()
  );
  ```
- **Hàm `acceptLoop()`:** Vòng lặp `serverSocket.accept()`. Khi có kết nối mới, kiểm tra số kết nối đang hoạt động trong `activeConnections`. Nếu chưa quá 20 thì tạo `ClientHandler` và gọi `workerPool.execute(handler)`. Nếu đã đầy thì từ chối và đóng socket ngay để bảo vệ Server.

### 3.2. `ClientHandler.java` (Worker Thread phục vụ 1 Client)
- Là một `Runnable` chạy trong worker thread riêng của client đó.
- Nhận diện trạng thái kết nối (`State.CONNECTED -> READY -> UPLOADING / DOWNLOADING -> CLOSING`).
- Phương thức `handleHello()`: Xác nhận bắt tay, gửi thông số cấu hình và chuyển sang `READY`.
- Phương thức `close()`: Sử dụng cờ `AtomicBoolean closed` để đảm bảo dù bị gọi nhiều lần (do timeout, đứt mạng hay lệnh QUIT) thì việc đóng socket và nhả luồng chỉ diễn ra đúng 1 lần.

### 3.3. `FileStorage.java` (Quản lý đọc/ghi file vật lý trên đĩa)
- **`cleanupTempFilesOnStartup()`:** Dùng `storageDir.listFiles((dir, name) -> name.endsWith(".tmp"))` để xóa sạch toàn bộ file `.tmp` mồ côi khi Server vừa bật máy.
- **`openTempFile(uploadId)`:** Mở file `storageDir/<uploadId>.tmp` bằng `RandomAccessFile(f, "rw")` để ghi dữ liệu từng chunk.
- **`saveCompletedFile(uploadId, fileId)`:** Đổi tên nguyên tử file tạm thành file chính thức bằng lệnh:
  `Files.move(temp.toPath(), completed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);`

### 3.4. `FileManager.java` (Quản lý Danh mục và Hạn ngạch Quota)
- **`reserveSpace(fileSize)`:** Kiểm tra và dành chỗ dung lượng 10 GiB bằng giải thuật CAS không khóa:
  ```java
  while (true) {
      long currentReserved = reservedBytes.get();
      long currentUsed = usedBytes.get();
      if (currentUsed + currentReserved + bytes > storageQuotaBytes) {
          throw new RequestException(Protocol.STATUS_STORAGE_UNAVAILABLE, "Hết dung lượng lưu trữ!");
      }
      if (reservedBytes.compareAndSet(currentReserved, currentReserved + bytes)) {
          break; // Dành chỗ thành công không cần lock!
      }
  }
  ```
- **`listFiles(pageIndex, pageSize)`:** Áp dụng phân trang (Pagination). Trích xuất bản sao danh sách file rồi mới cắt trang 50 file để gửi cho client.

### 3.5. `ConnectionWatcher.java` (Quét dọn timeout ngầm)
- Chạy ngầm định kỳ mỗi 5 giây (`Thread.sleep(5000)`).
- Kiểm tra các socket không gửi dữ liệu quá 60s (`Protocol.TIMEOUT_READ_MS`) để ngắt kết nối.
- Gọi `sessionManager.removeExpiredSessions()` để thu hồi các phiên tải lên bị bỏ dở quá 30 phút.

### 3.6. `ServerMain.java`, `Server.java` & `ServerConfig.java` (Khởi chạy và nạp cấu hình)
- **`Server.java`:** Lớp entrypoint thực thi từ dòng lệnh ngoài thư mục gốc, chuyển tiếp trực tiếp các cờ và tham số cấu hình vào `ServerMain.main(args)`.
- **`ServerConfig.java`:** Nạp các thông số hoạt động của server (cổng TCP mặc định 5000, thư mục lưu trữ `storage/`, số client tối đa 20, hạn ngạch Quota 10 GiB, timeout bắt tay và đọc dữ liệu) từ file cấu hình hoặc biến môi trường.
- **`ServerMain.java`:** Điều phối toàn bộ vòng đời khởi động của server:
  1. Khởi tạo các thành phần dùng chung: `FileStorage`, `FileManager`, `SessionManager`.
  2. Gọi `fileStorage.cleanupTempFilesOnStartup()` để quét dọn các file `.tmp` mồ côi còn sót lại từ phiên chạy trước.
  3. Khởi động `ConnectionListener` (ThreadPool) và `ConnectionWatcher` (luồng quét timeout).
  4. Khởi động `DiscoveryServer` trên cổng UDP 5001 để tự động phản hồi client tìm kiếm.
  5. Đăng ký `Runtime.getRuntime().addShutdownHook()` để đóng ServerSocket, giải phóng ThreadPool và ngắt kết nối an toàn (Graceful Shutdown) khi tắt server.

---

# 4. KỊCH BẢN VẤN ĐÁP KHI THẦY HỎI (MOCK DEFENSE CHO TƯỜNG)

### Câu 1: "Tại sao em dùng `ThreadPoolExecutor` mà không dùng `new Thread()` cho mỗi client?"
> **Trả lời:**  
> *"Dạ thưa Thầy, trong máy ảo Java, mỗi `Thread` tương ứng với một thread thực thi của nhân hệ điều hành (Native OS Thread) và được cấp phát một vùng nhớ stack riêng biệt (~1 MB). Nếu dùng `new Thread()` tự do, khi có nhiều client kết nối ồ ạt hoặc kẻ xấu cố tình mở hàng trăm kết nối ảo, bộ nhớ của hệ thống sẽ cạn kiệt, dẫn đến lỗi sập toàn bộ máy chủ: `OutOfMemoryError: unable to create new native thread`.*  
> *Vì vậy, em dùng `ThreadPoolExecutor` cố định 20 luồng công việc kết hợp hàng đợi `ArrayBlockingQueue`. Tổng bộ nhớ stack của toàn bộ worker thread chỉ tốn đúng 20 MB, giúp máy chủ luôn ổn định tuyệt đối."*

### Câu 2: "Tại sao em phân chia `FileStorage` và `FileManager` thành 2 lớp riêng?"
> **Trả lời:**  
> *"Dạ thưa Thầy, em áp dụng nguyên lý Đơn trách nhiệm (Single Responsibility):*  
> *- `FileStorage` chỉ làm việc với hệ thống file vật lý trên đĩa cứng: tạo file `.tmp`, ghi byte thô, đổi tên nguyên tử sang `.dat` và dọn file mồ côi lúc khởi động.*  
> *- `FileManager` hoàn toàn không ghi nội dung file, mà chỉ quản lý danh mục metadata trong bộ nhớ RAM (`Map<String, FileInfo>`) và quản lý hạn ngạch lưu trữ Quota 10 GiB bằng các biến nguyên tử `AtomicLong`.*  
> *Sự tách biệt này giúp tầng lưu trữ vật lý và tầng logic nghiệp vụ không bị phụ thuộc chéo vào nhau."*

### Câu 3: "Làm thế nào hệ thống của em không bao giờ xảy ra Deadlock?"
> **Trả lời:**  
> *"Dạ thưa Thầy, em triệt tiêu Deadlock bằng 3 giải pháp phối hợp:*  
> *1. **Thứ tự khóa 1 chiều:** Em quy định thứ bậc khóa cố định: `SessionManager` (ngoài cùng) $\rightarrow$ `TransferSession` (ở giữa) $\rightarrow$ `FileManager` (trong cùng). Cấm tuyệt đối `FileManager` gọi ngược lại các phương thức có khóa của hai lớp trên, phá vỡ hoàn toàn điều kiện chờ vòng tròn.*  
> *2. **Nguyên tắc Snapshot nhả khóa ngay:** Khi upload hoàn tất hoặc dọn dẹp, luồng chỉ khóa session chốc lát để lấy dữ liệu ra biến riêng rồi nhả khóa ngay, sau đó mới gọi sang lớp khác. Không bao giờ giữ lồng 2 khóa cùng lúc.*  
> *3. **CAS không khóa cho Quota:** Thao tác kiểm tra dung lượng Quota dùng `AtomicLong.compareAndSet`, xử lý ở cấp độ lệnh phần cứng vi xử lý mà không cần dùng từ khóa `synchronized`, nên hoàn toàn không thể bị deadlock."*

---

# 5. CÁC CÂU HỎI MẪU ĐỂ BẠN HỎI AI TỰ HỌC & ĐÀO SÂU MÃ NGUỒN

Khi muốn AI giải thích cặn kẽ để hiểu sâu từng dòng code mình phụ trách, bạn hãy copy trực tiếp các câu lệnh (prompts) dưới đây vào AI:

### Prompt 1: Về ThreadPool & Quản lý Socket quá tải
```text
Tôi đang phụ trách phần Server Core trong đồ án Lập trình mạng Java (INT1433).
Hãy phân tích cho tôi file ConnectionListener.java:
1. Tại sao lại cấu hình ThreadPoolExecutor với corePoolSize = 20, maxPoolSize = 20, keepAliveTime = 60s và hàng đợi ArrayBlockingQueue(40) thay vì dùng Executors.newCachedThreadPool() hay new Thread() tự do?
2. Khi client thứ 21 hoặc 61 kết nối tới, ServerSocket.accept() và ThreadPool xử lý như thế nào? Chính sách AbortPolicy hoạt động ra sao và code của tôi đã chủ động đóng socket từ chối như thế nào để không làm sập server?
3. Hãy giải thích chi tiết mức tiêu thụ bộ nhớ Stack (Thread Stack ~1MB) của 20 luồng worker so với việc tạo không giới hạn luồng.
```

### Prompt 2: Về thuật toán CAS không khóa cho Quota
```text
Tôi đang phụ trách lớp FileManager.java trong đồ án Lập trình mạng.
Hãy đọc phương thức reserveSpace(long bytes) và giải thích:
1. Tại sao lại dùng vòng lặp while (true) kết hợp reservedBytes.compareAndSet(currentReserved, currentReserved + bytes) thay vì dùng từ khóa synchronized?
2. Giải thuật Compare-And-Set (CAS) hoạt động ở cấp độ chỉ thị vi xử lý (CPU instruction CMPXCHG) như thế nào?
3. Nếu 20 luồng client đồng thời upload các file có dung lượng khác nhau làm tổng dung lượng tiệm cận 10 GiB, giải thuật CAS này giải quyết xung đột (Race Condition) và bảo vệ hạn ngạch dung lượng tuyệt đối ra sao mà không gây suy giảm hiệu năng?
```

### Prompt 3: Về Tầng Lưu Trữ Đĩa & Di Chuyển Nguyên Tử (Atomic Move)
```text
Tôi đang phụ trách lớp FileStorage.java và FileManager.java.
Hãy phân tích cho tôi:
1. Tại sao kiến trúc hệ thống lại tách biệt FileStorage (ghi đĩa) và FileManager (quản lý RAM) thay vì gộp chung vào một lớp?
2. Trong phương thức saveCompletedFile(), tại sao tôi lại dùng Files.move(temp.toPath(), completed.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)? Bản chất của lệnh "Atomic Move" này trên hệ thống tệp tin (File System như NTFS trên Windows hoặc ext4 trên Linux) là gì?
3. Phương thức cleanupTempFilesOnStartup() có vai trò gì khi máy chủ gặp sự cố mất điện hoặc crash đột ngột trong lúc đang nhận dở dữ liệu?
```

### Prompt 4: Về Chứng Minh Triệt Tiêu Deadlock (Anti-Deadlock Architecture)
```text
Tôi cần chuẩn bị trả lời vấn đáp về An toàn luồng (Thread Safety) và Chống Deadlock.
Trong hệ thống Server của tôi có 3 lớp có khóa: SessionManager, TransferSession và FileManager.
1. Hãy phân tích thứ tự lấy khóa 1 chiều (Strict Lock Hierarchy) đã được thiết kế trong code: SessionManager -> TransferSession -> FileManager.
2. Nếu ai đó vô tình viết thêm code để FileManager gọi ngược lại một hàm synchronized của SessionManager, kịch bản Deadlock nào sẽ xảy ra? Hãy vẽ sơ đồ 2 luồng giữ khóa của nhau để minh họa.
3. Nguyên tắc "Snapshot nhả khóa ngay" (Snapshot before Calling) được cài đặt như thế nào để đảm bảo không một luồng nào giữ lồng 2 khóa cùng một lúc?
```

### Prompt 5: Về Vòng đời ClientHandler & Thu hồi tài nguyên
```text
Hãy đọc lớp ClientHandler.java và ConnectionWatcher.java của tôi:
1. Giải thích vòng đời một kết nối từ khi accept() -> HELLO -> READY -> UPLOADING -> CLOSING.
2. Tại sao phương thức close() trong ClientHandler lại cần cờ AtomicBoolean closed = new AtomicBoolean(false)? Nếu cả 2 luồng (Worker Thread và ConnectionWatcher Thread) cùng gọi close() một lúc thì điều gì xảy ra?
3. Cơ chế quét ngầm ConnectionWatcher mỗi 5 giây thu hồi các kết nối rác (quá hạn 60s) và dọn các session bỏ dở (quá 30 phút) như thế nào để ngăn chặn Memory Leak?
```

---

# 6. DANH SÁCH TỪ KHÓA BẠN PHẢI NÓI KHI BẢO VỆ
Khi trả lời Thầy Hùng, hãy chủ động nhắc đến các cụm từ này để lấy trọn điểm chuyên sâu:
1. **Controlled Thread Pool (`ThreadPoolExecutor`):** Khống chế 20 worker threads, tránh cạn kiệt Native OS Thread Stack (tránh `OutOfMemoryError`).
2. **Lock-free CAS Quota:** Kiểm tra và dành chỗ dung lượng 10 GiB bằng `AtomicLong.compareAndSet`, xử lý ở cấp độ phần cứng vi xử lý mà không cần khóa màn trập `synchronized`.
3. **Strict Lock Hierarchy:** Quy định thứ tự lấy khóa 1 chiều (`SessionManager` $\rightarrow$ `TransferSession` $\rightarrow$ `FileManager`), triệt tiêu 100% điều kiện chờ vòng tròn gây Deadlock.
4. **Snapshot before Processing:** Sao chép dữ liệu cần thiết ra biến cục bộ và nhả khóa ngay lập tức trước khi gọi sang lớp khác, không bao giờ giữ lồng 2 khóa cùng lúc.
5. **Atomic Rename (`ATOMIC_MOVE`):** Đổi tên file tạm `.tmp` thành `.dat` ở mức con trỏ inode của hệ điều hành, đảm bảo client khác không bao giờ nhìn thấy file chưa hoàn chỉnh.
6. **Bootstrap Cleanup:** Tự động quét và xóa sạch toàn bộ tệp mồ côi `.tmp` ngay khi khởi động lại máy chủ.


