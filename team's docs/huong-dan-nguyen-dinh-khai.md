# HƯỚNG DẪN HỌC & BẢO VỆ ĐỒ ÁN CHO NGUYỄN ĐÌNH KHẢI (B23DCCN420)
## VAI TRÒ: PHỤ TRÁCH CLIENT ARCHITECTURE, UI CONCURRENCY, UDP DISCOVERY & PROGRESS TRACKING
**Môn học:** Lập trình mạng (INT1433) · PTIT  
**Giảng viên:** TS. Đặng Ngọc Hùng  

---

# 1. TỔNG QUAN PHẠM VI TRÁCH NHIỆM

Bạn là người chịu trách nhiệm về **"trải nghiệm người dùng, tính tương tác thời gian thực và khả năng thích ứng mạng"** phía Client:
1. Thiết kế kiến trúc **Client 2 luồng (2-Thread Concurrency)**: Luồng chính tương tác dòng lệnh (`ConsoleUI`) hoàn toàn tách biệt với luồng công nhân mạng nền (`TransferClient`), giúp giao diện không bao giờ bị đơ (freeze).
2. Điều khiển truyền tải không khóa trong thời gian thực: Hỗ trợ lệnh tạm dừng (`pause`), tiếp tục (`resume`) và hủy bỏ (`cancel`) an toàn tuyệt đối bằng cờ `volatile`.
3. Dò tìm máy chủ tự động trong mạng LAN qua giao thức **UDP Broadcast** (`DiscoveryClient`), có cơ chế dự phòng (fallback) khi chạy qua Router/NAT/Docker.
4. Quản lý tiến trình truyền tải trực quan (`TransferProgress`): Thuật toán lấy mẫu trung bình trượt 300ms tính tốc độ `MB/s`, ước tính thời gian còn lại `ETA`, vẽ thanh tiến trình ASCII cập nhật tại chỗ bằng ký tự `\r`.
5. Quản lý trạng thái lưu tạm phía Client (`ResumeManager`): Lưu checkpoint xuống tệp đĩa `.state` để phục hồi truyền tiếp từ vị trí gián đoạn mà không tốn công truyền lại từ đầu.

---

# 2. CÁC TÀI LIỆU BẠN BẮT BUỘC PHẢI ĐỌC TRONG `TMP/`

1. **[`tmp/architectureV2.md`](file:///d:/project/BTL%20LTM/tmp/architectureV2.md):**
   - **Mục 1.2:** Mô hình Client đa luồng (UI Thread + SingleThreadExecutor Worker Thread).
   - **Mục 2.1:** Sơ đồ Class Diagram phía Client (`ClientMain`, `ConsoleUI`, `TransferClient`, `DiscoveryClient`, `ResumeManager`, `TransferProgress`).
   - **Mục 4:** Vòng đời kết nối (`Connection Lifecycle`) từ góc nhìn Client.
2. **[`tmp/note.md`](file:///d:/project/BTL%20LTM/tmp/note.md):**
   - **Mục 2:** Bản chất bộ nhớ đệm vi xử lý và vì sao phải dùng từ khóa `volatile` cho cờ điều khiển (`isPaused`, `isCancelled`).
   - **Mục 5:** Cơ chế UDP Broadcast phát thanh tìm máy chủ và lý do bị chặn trong mạng Docker / Router định tuyến.
   - **Mục 10.2:** Hành vi ứng xử của Client khi mất mạng đột ngột (Lưu checkpoint xuống đĩa và thông báo người dùng).
3. **[`tmp/key-word.md`](file:///d:/project/BTL%20LTM/tmp/key-word.md):**
   - Học thuộc các từ khóa của bạn: `Client 2-thread Architecture`, `volatile Memory Visibility`, `UDP Broadcast Auto-Discovery`, `Rolling Average Speed & ETA`, `Client-side Checkpointing (.state)`.

---

# 3. CÁC CLASS MÃ NGUỒN BẠN PHẢI NẮM NHƯ LÒNG BÀN TAY

Tất cả code của bạn nằm trong package: [`client/src/lftp/client/`](file:///d:/project/BTL%20LTM/int1433-05-20261-grp-int1433-05-0017/client/src/lftp/client/)

### 3.1. `ConsoleUI.java` (Luồng chính UI - Main Thread)
- **Vòng lặp tương tác:** Chạy `Scanner(System.in)` nhận các lệnh: `discover`, `connect`, `list`, `upload`, `download`, `pause`, `resume`, `cancel`, `pending`, `status`, `exit`.
- **Tách luồng xử lý:** Khởi tạo `transferExecutor = Executors.newSingleThreadExecutor()`.
  Khi người dùng gõ `upload` hoặc `download`, phương thức `handleUpload` không chạy chặn trên luồng chính mà gửi tác vụ vào executor:
  ```java
  activeTransferTask = transferExecutor.submit(() -> {
      try {
          client.uploadFile(file);
      } catch (Exception e) {
          System.out.println("\n!! LỖI TRUYỀN TẢI: " + e.getMessage());
      }
  });
  ```
  Nhờ đó, luồng chính `ConsoleUI` ngay lập tức rảnh tay để tiếp tục in dấu nhắc `LFTP> ` và nhận các lệnh can thiệp tiếp theo.
- **Xử lý can thiệp trực tiếp:**
  - `handlePause()` $\rightarrow$ gọi `client.pauseTransfer()`.
  - `handleResume()` $\rightarrow$ gọi `client.resumeTransfer()`.
  - `handleCancel()` $\rightarrow$ gọi `client.cancelTransfer()`.

### 3.2. `TransferClient.java` (Công nhân mạng nền - Network Worker Thread)
- **Cờ trạng thái `volatile`:**
  ```java
  private volatile boolean isPaused = false;
  private volatile boolean isCancelled = false;
  private volatile TransferProgress activeProgress;
  ```
- **Vòng lặp gửi chunk an toàn:** Trong hàm `uploadFile()`:
  - Trước mỗi lần đọc đĩa và gửi chunk 64 KiB, kiểm tra:
    ```java
    if (isCancelled) {
        System.out.println("\n[Upload] Người dùng đã hủy lượt truyền tệp!");
        resumeManager.removeUploadState(file.getAbsolutePath());
        return null;
    }
    while (isPaused) {
        Thread.sleep(200);
        if (isCancelled) break;
    }
    ```
  - Cập nhật tiến trình: `activeProgress.update(currentOffset); System.out.print(activeProgress.renderProgressBar(30));`
  - Ghi checkpoint xuống đĩa mỗi khi truyền được 1 MB:
    ```java
    if (currentOffset % (1024 * 1024) < negotiatedChunkSize) {
        resumeManager.saveUploadState(new ResumeManager.UploadState(
            uploadId, resumeToken, file.getAbsolutePath(), fileSize, fileHashHex, currentOffset, System.currentTimeMillis()));
    }
    ```

### 3.3. `DiscoveryClient.java` (Tự động dò tìm qua UDP Broadcast)
- **Phương thức `discoverServer(timeoutMs)`:**
  1. Mở `DatagramSocket socket = new DatagramSocket()`.
  2. Bật cờ phát thanh: `socket.setBroadcast(true)`.
  3. Cài đặt thời gian chờ tối đa 3 giây: `socket.setSoTimeout(3000)`.
  4. Đóng gói chuỗi `"LFTP_DISCOVER"` gửi tới địa chỉ broadcast toàn cục: `255.255.255.255` tại cổng `5001` (`Protocol.DEFAULT_UDP_PORT`).
  5. Đợi phản hồi `"LFTP_OFFER <serverName> <port>"` từ máy chủ. Bóc tách địa chỉ IP nguồn (`respPacket.getAddress().getHostAddress()`) và cổng TCP để tự động kết nối.
  6. Nếu hết 3 giây không có máy chủ phản hồi, bắt ngoại lệ `SocketTimeoutException` và in cảnh báo hướng dẫn người dùng nhập IP thủ công.

### 3.4. `TransferProgress.java` (Bộ đo tốc độ và vẽ tiến trình)
- **Thuật toán lấy mẫu trượt 300ms:** Tránh giật số và giảm tải CPU console bằng cách chỉ tính lại tốc độ khi thời gian trôi qua ít nhất 300ms:
  ```java
  long timeDelta = now - lastSampleTimeMs;
  if (timeDelta >= 300) {
      long bytesDelta = currentBytes - lastSampleBytes;
      double speedBytesPerSec = (bytesDelta * 1000.0) / timeDelta;
      this.currentSpeedMbps = speedBytesPerSec / (1024.0 * 1024.0); // Đổi sang MB/s
      this.lastSampleTimeMs = now;
      this.lastSampleBytes = currentBytes;
  }
  ```
- **Tính toán ETA:**
  $$\text{ETA (giây)} = \frac{\text{totalBytes} - \text{transferredBytes}}{\text{speedBytesPerSec}}$$
- **Vẽ đè trên 1 dòng console (`\r`):** Dùng ký tự Carriage Return `\r` để con trỏ nhảy về đầu dòng hiện tại, ghi đè chuỗi tiến trình mới mà không làm trôi dòng:
  `\r[==============>          ]  58.2% |  14.50 MB/s | ETA: 12s (58.2 MB / 100.0 MB)`

### 3.5. `ResumeManager.java` (Lưu vết Checkpoint đĩa cục bộ)
- **Đường dẫn thư mục:** Lưu trong `./client-state/`.
- **Tên file state:** Tính mã băm MD5 của đường dẫn tuyệt đối tệp nguồn để làm tên file (`<md5_path>.state`), tránh lỗi ký tự đặc biệt của hệ điều hành.
- **Nội dung file state:** Dùng `java.util.Properties` lưu 7 trường:
  `uploadId`, `resumeToken`, `localFilePath`, `fileSize`, `fileHashHex`, `lastOffset`, `savedAtEpochMs`.
- **Tự động đối soát:** Khi người dùng upload lại cùng tệp, `loadUploadState()` nạp lại dữ liệu, kiểm tra mã băm SHA-256 của file local xem có bị sửa đổi không. Nếu còn nguyên vẹn thì tự động gửi `UPLOAD_RESUME` lên server để truyền tiếp từ byte `lastOffset`.

---

# 4. KỊCH BẢN VẤN ĐÁP KHI THẦY HỎI (MOCK DEFENSE CHO KHẢI)

### Câu 1: "Tại sao Client của em phải chia thành 2 luồng? Nếu gộp chung trên 1 luồng thì hệ thống bị lỗi gì?"
> **Trả lời:**  
> *"Dạ thưa Thầy, nếu Client chỉ chạy trên 1 luồng duy nhất, khi thực hiện truyền tệp tin dung lượng lớn (vài trăm MB hoặc vài GB), luồng đó sẽ bị chặn hoàn toàn (Blocking I/O) trong các lệnh đọc đĩa, gửi chunk TCP và chờ gói tin phản hồi từ Server.*  
> *Hậu quả là người dùng không thể gõ bất kỳ phím nào trên bàn phím; giao diện terminal bị 'đơ' hoàn toàn. Người dùng không thể bấm tạm dừng (pause), không thể hủy bỏ (cancel) và cũng không thể kiểm tra trạng thái.*  
> *Vì vậy, em áp dụng mô hình 2 luồng: Luồng chính (`ConsoleUI`) chuyên trách đọc lệnh từ bàn phím và phản hồi người dùng ngay tức thì; Tác vụ gửi/nhận mạng nặng được giao cho luồng công nhân nền (`transferExecutor`). Hai luồng phối hợp nhịp nhàng mà không làm nghẽn giao diện."*

### Câu 2: "Tại sao biến `isPaused` và `isCancelled` phải dùng từ khóa `volatile`? Dùng biến `boolean` thường thì chuyện gì xảy ra?"
> **Trả lời:**  
> *"Dạ thưa Thầy, trong kiến trúc máy tính hiện đại, mỗi nhân CPU (Core) đều có các tầng nhớ đệm riêng biệt (L1/L2 Cache). Luồng UI chạy trên một nhân CPU, còn luồng Worker truyền tải chạy trên một nhân CPU khác.*  
> *Nếu chỉ khai báo `boolean isPaused = false` thông thường, luồng Worker khi chạy vòng lặp gửi chunk liên tục có thể đọc giá trị cờ từ chính L1 Cache của nó để tăng tốc độ thay vì đọc lại từ bộ nhớ RAM chính. Khi luồng UI cập nhật `isPaused = true`, giá trị này chỉ nằm ở Cache của luồng UI hoặc RAM mà luồng Worker không nhìn thấy được (đây là lỗi vi phạm tính nhìn thấy - **Memory Visibility Violation**).*  
> *Từ khóa `volatile` đảm bảo rằng mọi thao tác ghi vào biến sẽ được đẩy ngay xuống bộ nhớ RAM chính và mọi thao tác đọc từ bất kỳ luồng nào cũng phải lấy trực tiếp từ RAM chính (xác lập quan hệ `happens-before`), giúp luồng Worker dừng ngay lập tức khi người dùng nhập lệnh."*

### Câu 3: "Cơ chế UDP Broadcast tự tìm Server hoạt động như thế nào? Trong trường hợp nào thì cơ chế này không tìm được Server?"
> **Trả lời:**  
> *"Dạ thưa Thầy, lớp `DiscoveryClient` của em hoạt động theo 3 bước:*  
> *1. Mở một `DatagramSocket`, bật cờ `socket.setBroadcast(true)` và đặt thời gian chờ tối đa 3 giây bằng `socket.setSoTimeout(3000)`.*  
> *2. Gửi một gói tin UDP chứa thông điệp `LFTP_DISCOVER` tới địa chỉ broadcast toàn mạng `255.255.255.255` tại cổng UDP 5001.*  
> *3. Máy chủ chạy `DiscoveryServer` trong mạng LAN lắng nghe cổng 5001 sẽ nhận được và phát lại gói tin `LFTP_OFFER <ServerName> <TcpPort>`. Client nhận gói tin phản hồi này, trích xuất địa chỉ IP người gửi (`packet.getAddress().getHostAddress()`) và cổng TCP để tự động kết nối.*  
> *Trường hợp không dùng được: Gói tin UDP Broadcast chỉ hoạt động trong cùng một miền quảng bá (cùng mạng LAN cục bộ / cùng subnet). Nếu Client và Server nằm ở 2 mạng khác nhau qua Router định tuyến, hoặc chạy qua mạng Internet công cộng (NAT), hoặc chạy trong các Docker container nằm ở các cầu mạng ảo (bridge) khác nhau thì router/docker sẽ chặn gói tin broadcast. Khi đó, Client của em sẽ bắt ngoại lệ Timeout sau 3 giây và chuyển sang cơ chế dự phòng (Fallback): cho phép người dùng gõ lệnh `connect <ip> <port>` trực tiếp."*

### Câu 4: "Em tính tốc độ truyền tải và thời gian còn lại (ETA) như thế nào để số liệu không bị nhảy loạn xạ trên màn hình?"
> **Trả lời:**  
> *"Dạ thưa Thầy, trong lớp `TransferProgress`, nếu mỗi chunk truyền xong mà em tính tốc độ ngay thì số liệu sẽ bị dao động rất mạnh (Jitter) do độ trễ tức thời của mạng và đĩa cứng.*  
> *Do đó, em áp dụng thuật toán lấy mẫu trung bình trượt theo chu kỳ thời gian (Rolling Average Interval) tối thiểu 300ms: Em lấy số byte truyền được trong khoảng thời gian đó chia cho delta thời gian thực tế ($\Delta t$) để tính tốc độ trung bình theo MB/s.*  
> *Sau đó, thời gian còn lại được tính bằng công thức: $\text{ETA} = \frac{\text{Dung lượng còn lại}}{\text{Tốc độ trung bình}}$.*  
> *Để hiển thị mượt mà trên console mà không làm trôi dòng cuộn màn hình, em dùng ký tự điều khiển về đầu dòng `\r` (Carriage Return) để ghi đè thanh tiến độ trên đúng một dòng duy nhất."*

---

# 5. CÁC CÂU HỎI MẪU ĐỂ BẠN HỎI AI TỰ HỌC & ĐÀO SÂU MÃ NGUỒN

Khi muốn AI giải thích cặn kẽ để hiểu sâu từng dòng code và cơ chế giao diện/mạng phía Client, bạn hãy copy trực tiếp các câu lệnh (prompts) dưới đây vào AI:

### Prompt 1: Về Mô Hình Đa Luồng Client (UI Thread vs Network Worker)
```text
Tôi đang phụ trách phần Client trong đồ án Lập trình mạng Java (INT1433).
Hãy đọc lớp ConsoleUI.java và TransferClient.java:
1. Tại sao ConsoleUI lại khởi tạo transferExecutor = Executors.newSingleThreadExecutor() và submit tác vụ tải file qua activeTransferTask = transferExecutor.submit(...) thay vì chạy thẳng trên luồng chính?
2. Nếu tôi gọi trực tiếp client.uploadFile() trên luồng chính của Scanner, hiện tượng nghẽn I/O (Blocking I/O) sẽ khiến giao diện dòng lệnh bị đơ cứng như thế nào?
3. Khi tác vụ tải file đang chạy nền, luồng chính làm thế nào để tiếp tục nhận lệnh của người dùng (như pause, resume, cancel, status) một cách trơn tru?
```

### Prompt 2: Về Từ Khóa `volatile` và Điều Khiển Truyền Tải Không Khóa
```text
Trong TransferClient.java, tôi khai báo các cờ điều khiển:
private volatile boolean isPaused = false;
private volatile boolean isCancelled = false;
Hãy phân tích cho tôi:
1. Tại sao phải dùng từ khóa volatile mà không dùng boolean thông thường?
2. Hãy giải thích cơ chế phần cứng của CPU (bộ nhớ đệm L1/L2 Cache của từng Core so với RAM) và nguyên lý Memory Visibility: Nếu không có volatile, tại sao luồng Worker đang chạy vòng lặp gửi chunk có thể không bao giờ nhìn thấy lệnh pause mà luồng UI vừa ghi?
3. Tại sao việc dùng volatile kết hợp Thread.sleep(200) khi tạm dừng lại tối ưu hơn nhiều so với việc dùng khối đồng bộ hóa synchronized hoặc Object.wait()/notify()?
```

### Prompt 3: Về Dò Tìm Máy Chủ Tự Động Bằng UDP Broadcast & Giới Hạn Mạng
```text
Hãy đọc lớp DiscoveryClient.java của tôi:
1. Phân tích từng bước hoạt động của phương thức discoverServer(): từ việc mở DatagramSocket, bật socket.setBroadcast(true), gửi thông điệp LFTP_DISCOVER tới 255.255.255.255:5001 cho đến khi nhận phản hồi LFTP_OFFER.
2. Tại sao tôi phải cài đặt socket.setSoTimeout(3000)? Nếu không có timeout này thì điều gì xảy ra khi trong mạng không có server nào đang chạy?
3. Tại sao gói tin UDP Broadcast chỉ hoạt động trong mạng nội bộ (LAN Subnet) mà không thể xuyên qua Router Internet hay giữa các Container Docker chạy ở các mạng bridge ảo khác nhau? Cơ chế dự phòng (Fallback) khi không tìm thấy server trong code của tôi hoạt động ra sao?
```

### Prompt 4: Về Thuật Toán Đo Tốc Độ Lấy Mẫu Trượt (Rolling Average) & Vẽ Thanh Tiến Trình
```text
Hãy đọc lớp TransferProgress.java của tôi:
1. Trong phương thức update(long currentBytes), tại sao tôi lại dùng điều kiện if (timeDelta >= 300) để lấy mẫu tốc độ thay vì tính toán ngay sau mỗi chunk 64 KiB nhận được? Hiện tượng giật số (Jitter) sẽ xảy ra thế nào nếu không có khoảng trễ lấy mẫu này?
2. Hãy giải thích công thức toán học tính tốc độ truyền tải (MB/s) và công thức ước tính thời gian hoàn thành còn lại (ETA).
3. Trong phương thức renderProgressBar(), ký tự điều khiển đầu dòng \r (Carriage Return) hoạt động ra sao trong terminal để thanh tiến trình [=====>   ] 60% liên tục được cập nhật tại chỗ mà không bị in xuống dòng mới làm trôi màn hình?
```

### Prompt 5: Về Quản Lý Điểm Kiểm Tra (Checkpoint & Resume) Phía Client
```text
Hãy đọc lớp ResumeManager.java của tôi:
1. Tại sao tôi lại dùng hàm băm MD5 của đường dẫn tuyệt đối tệp tin nguồn (localFilePath) để đặt tên cho file trạng thái (<md5>.state) trong thư mục ./client-state/? Lợi ích của việc này đối với các ký tự đặc biệt trong đường dẫn file trên Windows/Linux là gì?
2. Tệp .state lưu những thông tin gì (uploadId, resumeToken, fileHashHex, lastOffset...)?
3. Khi người dùng thực hiện upload lại một file, lớp TransferClient và ResumeManager phối hợp kiểm tra những điều kiện gì để quyết định gửi lệnh UPLOAD_RESUME tiếp tục từ offset cũ thay vì gửi UPLOAD_INIT truyền lại từ đầu?
```

---

# 6. DANH SÁCH TỪ KHÓA BẠN PHẢI NÓI KHI BẢO VỆ
Khi trả lời Thầy Hùng, hãy chủ động nhắc đến các cụm từ này để lấy trọn điểm chuyên sâu:
1. **Client 2-thread Concurrency:** Tách riêng UI Main Thread và SingleThreadExecutor Network Worker.
2. **`volatile` Memory Visibility:** Đảm bảo tính nhìn thấy trực tiếp giữa các CPU Cores mà không cần khóa nặng `synchronized`.
3. **UDP Broadcast Auto-Discovery:** Gửi gói tin `255.255.255.255:5001` và cơ chế Fallback sang Direct TCP Connect.
4. **Rolling Average Interval (300ms) & ETA:** Thuật toán tính tốc độ trung bình trượt và thời gian hoàn thành còn lại.
5. **In-place Carriage Return (`\r`):** Cập nhật thanh tiến trình ASCII thời gian thực trên cùng một dòng.
6. **Client-side Checkpointing (.state):** Quản lý phiên truyền dở bằng file Properties dựa trên MD5 đường dẫn.


