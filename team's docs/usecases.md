# ĐẶC TẢ CHI TIẾT CÁC USE CASE & CƠ CHẾ VẬN HÀNH HỆ THỐNG LFTP/1
## TÀI LIỆU GIẢI PHẪU LUỒNG THỰC THI (CLASS & METHOD TRACEABILITY MATRIX)
**Dự án:** LAN File Transfer Protocol Version 1 (LFTP/1)  
**Mã nguồn:** Thư mục [`codeV2/`](file:///d:/project/BTL%20LTM/codeV2)  
**Đối tượng:** Dành cho kỹ sư hệ thống, sinh viên bảo vệ đồ án và giảng viên phản biện kiểm tra luồng thực thi.

---

> [!NOTE]
> **Mục đích tài liệu:**  
> Tài liệu này mô tả chi tiết và toàn diện **toàn bộ 10 Use Case (Kịch bản sử dụng)** mà hệ thống LFTP/1 hiện tại đang xử lý. Với từng use case, tài liệu chỉ rõ:
> - Tác nhân kích hoạt và dữ liệu đầu vào.
> - Luồng gọi hàm cụ thể: **Lớp nào gọi phương thức gì, truyền tham số gì, và để giải quyết mục đích gì**.
> - Khung thông điệp nhị phân trao đổi trên dây mạng (Opcode, Payload).
> - Sự biến đổi trạng thái tài nguyên phần cứng (CPU, RAM, Socket, Đĩa cứng File System, Hạn ngạch Quota CAS).

---

# DANH MỤC CÁC USE CASE HỆ THỐNG XỬ LÝ

| Mã UC | Tên Use Case | Tác nhân chính | Phạm vi thành phần tham gia |
|:---:|---|:---:|---|
| **UC01** | [Khởi động Server & Quét dọn tệp mồ côi](#use-case-01-khởi-động-server--dọn-dẹp-tệp-tạm-mồ-côi-bootstrap) | Quản trị viên / OS | `ServerMain`, `FileStorage`, `FileManager`, `SessionManager`, `ConnectionListener`, `DiscoveryServer`, `ConnectionWatcher` |
| **UC02** | [Tự động phát hiện Server trong LAN qua UDP Broadcast](#use-case-02-tự-động-phát-hiện-server-trong-lan-qua-udp-broadcast) | Người dùng / Client | `ConsoleUI`, `DiscoveryClient`, `DiscoveryServer`, `TransferClient` |
| **UC03** | [Bắt tay TCP & Thống nhất giới hạn (Handshake)](#use-case-03-bắt-tay-tcp--thống-nhất-giới-hạn-handshake-hello) | Client Network Worker | `TransferClient`, `ConnectionListener`, `ClientHandler`, `MessageIO`, `ThreadPoolExecutor` |
| **UC04** | [Xem danh mục tệp có phân trang (Pagination)](#use-case-04-xem-danh-mục-tệp-hoàn-tất-có-phân-trang-list-files) | Người dùng Client | `ConsoleUI`, `TransferClient`, `MessageIO`, `ClientHandler`, `FileManager` |
| **UC05** | [Tải lên tệp mới trọn vẹn (Progressive SHA-256)](#use-case-05-tải-lên-tệp-mới-trọn-vẹn-upload-with-streaming-hash) | Người dùng Client | `ConsoleUI`, `TransferClient`, `ResumeManager`, `MessageIO`, `ClientHandler`, `SessionManager`, `FileManager`, `UploadManager`, `FileStorage`, `HashUtils` |
| **UC06** | [Tạm dừng, Tiếp tục và Hủy bỏ lượt truyền tải (In-flight Control)](#use-case-06-tạm-dừng-tiếp-tục-và-hủy-bỏ-lượt-truyền-tải) | Người dùng Client | `ConsoleUI` (Luồng 1), `TransferClient` (Luồng 2), `TransferProgress`, `ResumeManager` |
| **UC07** | [Khôi phục lượt tải lên khi bị đứt mạng (Resume Upload)](#use-case-07-khôi-phục-lượt-tải-lên-khi-bị-đứt-mạng-resume-upload) | Người dùng / Mạng rớt | `TransferClient`, `ResumeManager`, `MessageIO`, `ClientHandler`, `SessionManager`, `FileStorage`, `HashUtils`, `UploadManager` |
| **UC08** | [Tải về tệp & Xác minh tính toàn vẹn (Download & Verify)](#use-case-08-tải-về-tệp--xác-minh-tính-toàn-vẹn-download-file) | Người dùng Client | `ConsoleUI`, `TransferClient`, `MessageIO`, `ClientHandler`, `FileManager`, `DownloadManager`, `FileStorage`, `HashUtils` |
| **UC09** | [Quét dọn định kỳ & Thu hồi tài nguyên quá hạn (Watcher Sweeper)](#use-case-09-quét-dọn-định-kỳ--thu-hồi-tài-nguyên-quá-hạn) | Hệ thống (Daemon) | `ConnectionWatcher`, `ConnectionListener`, `ClientHandler`, `SessionManager`, `FileStorage`, `FileManager` |
| **UC10** | [Đóng kết nối lịch sự & Tắt an toàn Server (Graceful Shutdown)](#use-case-10-đóng-kết-nối-lịch-sự--tắt-an-toàn-server-graceful-shutdown) | Người dùng / Quản trị | `ConsoleUI`, `TransferClient`, `ClientHandler`, `ServerMain` (ShutdownHook), `ThreadPoolExecutor` |
| **UC11** | [Kiểm soát chặn xung đột truyền tải trên cùng một Client](#use-case-11-kiểm-soát-chặn-xung-đột-truyền-tải-trên-cùng-một-client) | Người dùng Client | `ConsoleUI` (Main Thread), `TransferClient`, `SingleThreadExecutor`, `MessageIO` |
| **UC12** | [Truyền tải song song đa Client (Multi-Client Concurrency)](#use-case-12-truyền-tải-song-song-đa-client-multi-client-concurrency) | Đa người dùng / Đa terminal | `ConnectionListener`, `ThreadPoolExecutor`, `ClientHandler`, `UploadManager`, `DownloadManager`, `FileStorage`, `FileManager` |

---

# CHI TIẾT TỪNG USE CASE

---

## USE CASE 01: KHỞI ĐỘNG SERVER & DỌN DẸP TỆP TẠM MỒ CÔI (BOOTSTRAP)

### 1. Ý nghĩa & Bối cảnh
Khi máy chủ được khởi động bằng lệnh `run-server.bat`, hệ thống phải chuẩn bị sẵn sàng toàn bộ tài nguyên (I/O, bộ đếm Quota, Thread Pool, socket lắng nghe) và đặc biệt phải **quét sạch các tệp tạm `.tmp` mồ côi** bị bỏ lại do lần tắt đột ngột trước đó để tránh rác ổ cứng.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Quản trị viên
    participant Main as ServerMain
    participant Storage as FileStorage
    participant FM as FileManager
    participant SM as SessionManager
    participant CL as ConnectionListener
    participant DS as DiscoveryServer
    participant CW as ConnectionWatcher

    Admin->>Main: Chạy ServerMain.main(args)
    Main->>Main: Tạo ServerConfig (cổng TCP 5000, UDP 5001, Quota 25GB)
    Main->>Storage: Khởi tạo FileStorage(storageDir)
    Main->>Storage: cleanupTempFilesOnStartup()
    Note over Storage: Quét và xóa toàn bộ file *.tmp mồ côi trên đĩa!
    Main->>FM: Khởi tạo FileManager(storageDir, quotaBytes)
    FM->>FM: loadCatalogFromDisk() (đọc catalog.idx để phục hồi danh mục)
    Main->>SM: Khởi tạo SessionManager(FileManager, FileStorage)
    Main->>CL: Khởi tạo ConnectionListener(...)
    Main->>CL: start() -> Khởi động ServerSocket(5000) & ThreadPoolExecutor(20)
    Main->>DS: Khởi tạo DiscoveryServer(config) -> start() (Lắng nghe UDP 5001)
    Main->>CW: Khởi tạo ConnectionWatcher(...) -> Khởi chạy luồng daemon (quét mỗi 10s)
    Main->>Main: Đăng ký Runtime.addShutdownHook()
```

### 3. Bảng phân tích chi tiết: Lớp nào gọi phương thức gì để làm gì?

| Thứ tự | Lớp gọi (Caller) | Phương thức được gọi (Callee & Method) | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ServerMain` | `new ServerConfig(serverName: String, tcpPort: int, udpPort: int, storageDirPath: String)` | `serverName: String`, `tcpPort: int`, `udpPort: int`, `storageDirPath: String` | Nạp cấu hình từ CLI hoặc Environment Variables (hỗ trợ chạy Docker). |
| 2 | `ServerMain` | `new FileStorage(storageDir: File)` | `storageDir: File` | Tạo thư mục `./storage` trên đĩa cứng nếu chưa tồn tại. |
| 3 | `ServerMain` | `FileStorage.cleanupTempFilesOnStartup()` | Không | **Hành động sống còn:** Quét thư mục đĩa tìm mọi file có đuôi `.tmp` và `delete()`. Giải phóng toàn bộ dung lượng đĩa bị chiếm giữ do sự cố mất điện đột ngột trước đó. |
| 4 | `ServerMain` | `new FileManager(storageDir: File, quotaBytes: long)` | `storageDir: File`, `quotaBytes: long` | Khởi tạo bảng danh mục và các biến nguyên tử CAS Quota (`usedBytes: AtomicLong`, `reservedBytes: AtomicLong`). |
| 5 | `FileManager` | `FileManager.loadCatalogFromDisk()` | Không | Đọc danh mục các tệp hoàn tất từ lần chạy trước (`catalog.idx`) vào `catalog: ConcurrentHashMap<String, FileInfo>` trong RAM, cập nhật lại biến `usedBytes`. |
| 6 | `ServerMain` | `new SessionManager(fileManager: FileManager, fileStorage: FileStorage)` | `fileManager: FileManager`, `fileStorage: FileStorage` | Khởi tạo bảng lưu phiên truyền dở trong RAM bằng `ConcurrentHashMap<String, TransferSession>`. |
| 7 | `ConnectionListener` | `ConnectionListener.start()` | Không | Mở `ServerSocket` trên cổng 5000; tạo `ThreadPoolExecutor` với 5 core luồng, tối đa 20 worker threads và hàng đợi chặn `ArrayBlockingQueue(20)`. |
| 8 | `DiscoveryServer` | `DiscoveryServer.start()` | Không | Tạo `DatagramSocket` trên cổng UDP 5001, chạy luồng nền sẵn sàng nhận gói tin broadcast tìm kiếm. |
| 9 | `ServerMain` | `Thread.start()` | `target: Runnable` (`connectionWatcher: ConnectionWatcher`) | Bật luồng cảnh sát chạy ngầm định kỳ 10 giây một lần để phát hiện timeout. |
| 10 | `Runtime` | `Runtime.addShutdownHook(hook: Thread)` | `hook: Thread` | Bắt tín hiệu tắt ứng dụng (`Ctrl + C`) để dọn dẹp an toàn trước khi tiến trình JVM dừng. |

---

## USE CASE 02: TỰ ĐỘNG PHÁT HIỆN SERVER TRONG LAN QUA UDP BROADCAST

### 1. Ý nghĩa & Bối cảnh
Người dùng văn phòng mở ứng dụng Client mà không cần biết hoặc không cần nhớ địa chỉ IP cục bộ của Server (ví dụ `192.168.1.15`). Ứng dụng tự động phát gói tin Broadcast để phát hiện máy chủ.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as ConsoleUI
    participant DC as DiscoveryClient
    participant Net as Switch Mạng LAN
    participant DS as DiscoveryServer
    participant TC as TransferClient

    User->>UI: Bật Client hoặc gõ lệnh "discover"
    UI->>DC: discoverServer(timeoutMs = 2500)
    DC->>Net: Bắn gói UDP Broadcast tới 255.255.255.255:5001 ("LFTP_DISCOVER")
    Net->>DS: Chuyển tiếp gói tin tới Server trên cổng UDP 5001
    DS->>DS: Kiểm tra thông điệp bắt đầu bằng "LFTP_DISCOVER"
    DS->>Net: Phản hồi gói UDP Unicast về đúng IP/Port của Client ("LFTP_OFFER TKD-Server 5000")
    Net->>DC: Nhận gói phản hồi Unicast
    DC->>DC: Tách chuỗi lấy IP Server ("172.23.128.1") và Cổng TCP (5000)
    DC-->>UI: Trả về đối tượng ServerOffer(serverName, ip, tcpPort)
    UI->>TC: connect(ip, port) -> Kích hoạt UC03
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ConsoleUI` | `DiscoveryClient.discoverServer(timeoutMs: int)` | `timeoutMs: int` = 2500 | Yêu cầu tìm kiếm máy chủ với thời gian chờ tối đa 2.5 giây. |
| 2 | `DiscoveryClient` | `DatagramSocket.setBroadcast(on: boolean)` | `on: boolean` = `true` | Bật cờ cho phép gửi gói tin ra địa chỉ Broadcast toàn mạng con. |
| 3 | `DiscoveryClient` | `DatagramSocket.send(p: DatagramPacket)` | `p: DatagramPacket` (gói tin UDP tới `255.255.255.255:5001`, payload `"LFTP_DISCOVER"`) | Lan tỏa tín hiệu hỏi tìm server tới mọi máy tính trong cùng mạng LAN. |
| 4 | `DiscoveryServer` | `DatagramSocket.receive(p: DatagramPacket)` | `p: DatagramPacket` (đệm byte nhận) | Nhận gói tin trong luồng chạy ngầm của Server. |
| 5 | `DiscoveryServer` | `DatagramSocket.send(p: DatagramPacket)` | `p: DatagramPacket` (gói Unicast `"LFTP_OFFER <serverName> 5000"` gửi về `p.getSocketAddress()`) | Trả lời trực tiếp đích danh cho máy client vừa hỏi, cung cấp tên server và cổng TCP tiếp nhận dữ liệu. |
| 6 | `DiscoveryClient` | `new ServerOffer(serverName: String, ip: String, tcpPort: int)` | `serverName: String`, `ip: String`, `tcpPort: int` | Đóng gói kết quả tìm kiếm trả về cho UI. |
| 7 | `ConsoleUI` | `TransferClient.connect(host: String, port: int)` | `host: String`, `port: int` | Tự động chuyển tiếp sang bước kết nối TCP mà người dùng không cần gõ bàn phím. |

---

## USE CASE 03: BẮT TAY TCP & THỐNG NHẤT GIỚI HẠN (HANDSHAKE HELLO)

### 1. Ý nghĩa & Bối cảnh
Ngay sau khi kết nối TCP Socket thành công, hai bên phải bắt tay trong vòng **10 giây** để xác nhận phiên bản giao thức (`version = 1`) và trao đổi các hạn mức (kích thước chunk 64KB, trần khung 128KB, trần file 5GB). Nếu client không gửi `HELLO` đúng hạn, server sẽ ngắt kết nối để chống chiếm dụng tài nguyên.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    participant TC as TransferClient
    participant MIO_C as MessageIO (Client)
    participant CL as ConnectionListener
    participant TP as ThreadPoolExecutor
    participant CH as ClientHandler
    participant MIO_S as MessageIO (Server)

    TC->>TC: Socket.connect(host, port, timeout = 5000ms)
    CL->>CL: serverSocket.accept() chấp nhận kết nối TCP
    CL->>CL: Kiểm tra activeClients.size() < 20 (MAX_CLIENTS)
    CL->>TP: execute(new ClientHandler(socket, ...))
    TP->>CH: worker-thread chạy hàm run()
    CH->>CH: socket.setSoTimeout(10000ms) (HANDSHAKE_TIMEOUT)
    TC->>MIO_C: writeMessage(OP_HELLO, reqId=1, clientName)
    MIO_C->>MIO_S: Gửi khung nhị phân: [frameLen][1][1][reqId=1][status=0][clientName]
    MIO_S->>CH: readMessage() trả về đối tượng Message HELLO
    CH->>CH: handleHello(req): kiểm tra version == 1, gán clientName
    CH->>MIO_S: writeMessage(OP_HELLO, reqId=1, status=200, serverInfo)
    MIO_S->>MIO_C: Gửi phản hồi: [frameLen][1][1][reqId=1][200][serverName, connId, chunkSize...]
    CH->>CH: socket.setSoTimeout(60000ms) (READ_TIMEOUT thông thường)
    CH->>CH: Chuyển trạng thái state = State.READY
    MIO_C->>TC: Đọc phản hồi 200 OK -> Ghi nhận kết nối thành công
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `TransferClient` | `Socket.connect(endpoint: SocketAddress, timeout: int)` | `endpoint: SocketAddress` = `InetSocketAddress(host, port)`, `timeout: int` = 5000 | Mở kết nối TCP cấp hệ điều hành (bắt tay 3 bước SYN/SYN-ACK/ACK). |
| 2 | `ConnectionListener` | `ServerSocket.accept()` | Không | Đón nhận socket mới từ hàng đợi TCP backlog của hệ điều hành. |
| 3 | `ConnectionListener` | `ThreadPoolExecutor.execute(command: Runnable)` | `command: Runnable` = `new ClientHandler(...)` | Bàn giao socket cho một Worker Thread trong Thread Pool, giải phóng luồng chính để tiếp tục accept client khác. |
| 4 | `ClientHandler` | `Socket.setSoTimeout(timeout: int)` | `timeout: int` = 10000 (`TIMEOUT_HANDSHAKE_MS`) | Thiết lập đồng hồ đếm ngược: nếu quá 10s không có dữ liệu, socket sẽ văng lỗi `SocketTimeoutException` và tự ngắt kết nối. |
| 5 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 1: `OP_HELLO`, requestId = 1, status = 0, body = `clientName: String`) | Đóng khung nhị phân 12 byte header tiền tố độ dài gửi sang server. |
| 6 | `ClientHandler` | `MessageIO.readMessage()` | Không | Đọc đủ 4 byte độ dài và 12 byte header cố định, giải mã thông điệp. |
| 7 | `ClientHandler` | `ClientHandler.handleHello(req: Message)` | `req: Message` | Kiểm tra phiên bản `version == 1` (nếu sai trả mã lỗi 505); trích xuất `clientName: String`. |
| 8 | `ClientHandler` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 1: `OP_HELLO`, requestId = 1, status = 200, body: serverParams) | Gửi lại cho client các thông số đàm phán: `serverName: String`, `connectionId: String` (UUID duy nhất), `chunkSize: int` = 65536, `maxFileBytes: long` = 5GB, `maxFrameBytes: int` = 128KB. |
| 9 | `ClientHandler` | `Socket.setSoTimeout(timeout: int)` | `timeout: int` = 60000 (`TIMEOUT_READ_MS`) | Nới rộng thời gian chờ socket lên 60 giây cho các lệnh truyền dữ liệu tiếp theo. |
| 10 | `ClientHandler` | Gán `this.state: State` | `state: State` = `State.READY` | Kích hoạt máy trạng thái chuyển sang trạng thái sẵn sàng tiếp nhận các lệnh nghiệp vụ. |

---

## USE CASE 04: XEM DANH MỤC TỆP HOÀN TẤT CÓ PHÂN TRANG (LIST_FILES)

### 1. Ý nghĩa & Bối cảnh
Người dùng gõ lệnh `list` hoặc `list <page> <size>` để xem các file đang có trên server. Hệ thống bắt buộc phải **phân trang (Pagination)** để gói tin trả về không vượt quá giới hạn an toàn **128 KiB**, loại trừ hoàn toàn nguy cơ làm tràn bộ nhớ Heap của cả client và server khi số lượng file lên tới hàng chục nghìn.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as ConsoleUI
    participant TC as TransferClient
    participant MIO_C as MessageIO (Client)
    participant MIO_S as MessageIO (Server)
    participant CH as ClientHandler
    participant FM as FileManager

    User->>UI: Gõ lệnh "list 0 10" (hoặc chỉ "list")
    UI->>TC: listFiles(pageIndex = 0, pageSize = 10)
    TC->>MIO_C: writeMessage(OP_LIST_FILES, reqId, body: [pageIndex: 0][pageSize: 10])
    MIO_C->>MIO_S: Gửi khung dữ liệu TCP
    MIO_S->>CH: readMessage() -> Gọi handleListFiles(req)
    CH->>FM: listFiles(pageIndex = 0, pageSize = 10)
    FM->>FM: Tạo Snapshot: new ArrayList<>(catalog.values())
    FM->>FM: Sắp xếp theo createdAtEpochMillis giảm dần (file mới nhất lên đầu)
    FM->>FM: Cắt lấy trang 0 (subList: tối đa 10 phần tử)
    FM-->>CH: Trả về List<FileInfo> (tối đa 10 file)
    CH->>FM: getTotalFiles() -> Lấy tổng số lượng file trong danh mục
    CH->>MIO_S: writeMessage(OP_LIST_FILES, status=200, [totalFiles][page][count][entries])
    MIO_S->>MIO_C: Gửi phản hồi qua TCP
    MIO_C->>TC: readMessage() giải mã ra ListResult
    TC-->>UI: Trả về đối tượng ListResult
    UI->>User: In bảng danh mục định dạng đẹp (FileId, Tên, Kích thước, Hash ngắn, Ngày tạo)
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ConsoleUI` | `TransferClient.listFiles(pageIndex: int, pageSize: int)` | `pageIndex: int` = 0, `pageSize: int` = 10 | Yêu cầu lấy trang 0 với tối đa 10 tệp. |
| 2 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 2: `OP_LIST_FILES`, reqId: long, status = 0, payload: `writeInt(pageIndex)`, `writeInt(pageSize)`) | Đóng gói 2 số nguyên 4 byte `pageIndex: int` và `pageSize: int` gửi đi. |
| 3 | `ClientHandler` | `FileManager.listFiles(pageIndex: int, pageSize: int)` | `pageIndex: int` = 0, `pageSize: int` = 10 | Yêu cầu tầng quản lý dữ liệu cắt trang. |
| 4 | `FileManager` | `new ArrayList<>(c: Collection<? extends FileInfo>)` | `c: Collection<FileInfo>` = `catalog.values()` | **Nguyên tắc Snapshot:** Tạo một bản sao danh sách để thao tác sắp xếp không cần giữ khóa và không gây xung đột với luồng upload đang thêm file mới. |
| 5 | `List` | `List.sort(c: Comparator<? super FileInfo>)` | `c: Comparator<FileInfo>` (so sánh `createdAtEpochMillis: long` giảm dần) | Đảm bảo các tệp mới tải lên gần đây nhất luôn được hiển thị ở trang đầu tiên. |
| 6 | `FileInfo` | `FileInfo.writeToStream(out: DataOutputStream)` | `out: DataOutputStream` | Mỗi tệp được tuần tự hóa theo cấu trúc nhị phân: `fileId: String`, `displayName: String`, `fileSize: long`, `fileHash: byte[]`, `createdAt: long`. |
| 7 | `ClientHandler` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 2, reqId: long, status = 200, body: `[totalFiles: int, page: int, count: int, entries: byte[]]`) | Trả kết quả về cho client. Kích thước toàn bộ gói tin trang 10 file chỉ chiếm khoảng ~1.5 KiB, hoàn toàn an toàn dưới trần 128 KiB. |
| 8 | `ConsoleUI` | `System.out.println(x: String)` | `x: String` (bảng ASCII định dạng) | Hiển thị thông tin trực quan cho người dùng kèm câu hướng dẫn gõ `list <page>` để xem tiếp. |

---

## USE CASE 05: TẢI LÊN TỆP MỚI TRỌN VẸN (UPLOAD WITH STREAMING HASH)

### 1. Ý nghĩa & Bối cảnh
Người dùng gõ lệnh `upload <path_to_file>` để tải lên một file lớn (ví dụ 3.79 GB). Hệ thống thực hiện:
- Kiểm tra dung lượng và tính SHA-256 file nguồn.
- Kiểm tra hạn ngạch đĩa Quota bằng **CAS không khóa**.
- Khởi tạo phiên, tạo file `.tmp`.
- Bơm từng chunk **64 KiB** đồng thời tính băm **SHA-256 lũy tiến ngay trong RAM**.
- Lúc Commit: xác thực toàn vẹn tức thì ngay trong bộ nhớ RAM mà không cần đọc lại đĩa cứng và đổi tên nguyên tử thành file chính thức.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as ConsoleUI
    participant TC as TransferClient
    participant RM as ResumeManager
    participant CH as ClientHandler
    participant SM as SessionManager
    participant FM as FileManager
    participant UM as UploadManager
    participant FS as FileStorage
    participant Digest as MessageDigest (RAM)

    User->>UI: upload "D:/data/LabtainerVM.ova"
    UI->>UI: cleanPath() loại bỏ dấu nháy kép
    UI->>TC: uploadFile(File) trên luồng Worker ngầm
    TC->>TC: Kiểm tra fileSize <= 5 GiB
    TC->>TC: HashUtils.calculateFileSha256(file) -> Tính băm nguồn
    TC->>CH: Gửi UPLOAD_INIT (displayName, fileSize, fileHash)
    CH->>SM: createUploadSession(displayName, fileSize, fileHash, connId)
    SM->>FM: reserveSpace(fileSize) -> Dùng vòng lặp CAS AtomicLong kiểm tra Quota 25GB
    SM->>FS: openTempFile(uploadId) -> Tạo file vật lý uploadId.tmp trên đĩa
    SM-->>CH: Trả về đối tượng TransferSession (uploadId, resumeToken, offset=0)
    CH->>UM: new UploadManager(session, fileStorage, fileManager)
    CH-->>TC: Trả về phản hồi 201 CREATED (uploadId, resumeToken, nextOffset=0)
    TC->>RM: saveUploadState() -> Lưu checkpoint ban đầu xuống đĩa client
    
    loop Bơm từng chunk 64 KiB liên tục cho đến hết file
        TC->>TC: RandomAccessFile.read(buffer, 64 KiB)
        TC->>CH: Gửi UPLOAD_CHUNK (uploadId, token, offset, length=64KB, data)
        CH->>UM: writeChunk(offset, data, length)
        UM->>FS: raf.seek(offset) & raf.write(data) -> Ghi xuống đĩa
        UM->>Digest: session.updateDigest(data) -> Cập nhật băm lũy tiến vào RAM!
        UM-->>CH: Trả về nextOffset = offset + length
        CH-->>TC: Trả về 200 OK (nextOffset)
        TC->>TC: activeProgress.update() -> In thanh tiến độ (%, MB/s, ETA)
        opt Định kỳ mỗi 1 MB
            TC->>RM: saveUploadState() cập nhật lastOffset
        end
    end

    TC->>CH: Gửi UPLOAD_COMMIT (uploadId, resumeToken)
    CH->>UM: finishUpload()
    UM->>Digest: digest.digest() -> Lấy kết quả băm cuối cùng tức thì từ RAM (không đọc lại đĩa)!
    UM->>UM: HashUtils.matches(calculatedHash, sourceHash) -> Khớp 100%!
    UM->>FS: saveCompletedFile(uploadId, fileId) -> Đổi tên nguyên tử thành fileId.dat
    UM->>FM: addFile(completedInfo) -> Đưa vào danh mục catalog.idx
    UM->>FM: confirmSpace(fileSize) -> Chuyển Quota từ reserved sang used
    UM->>SM: session.markCompleted(fileId)
    UM-->>CH: Trả về FileInfo hoàn tất
    CH-->>TC: Trả về 200 OK (fileId, confirmedSize, confirmedHash)
    TC->>RM: removeUploadState() -> Xóa file checkpoint
    TC-->>UI: Thông báo UPLOAD HOÀN TẤT THÀNH CÔNG!
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ConsoleUI` | `ConsoleUI.cleanPath(path: String)` | `path: String` | Loại bỏ triệt để các dấu nháy kép `"` hoặc nháy đơn `'` do người dùng copy từ Windows Explorer. |
| 2 | `TransferClient` | `File.length()` | Không | Lấy kích thước tệp và kiểm tra điều kiện `fileSize: long <= Protocol.MAX_FILE_BYTES (5 GiB)`. |
| 3 | `TransferClient` | `HashUtils.calculateFileSha256(file: File)` | `file: File` | Đọc tuần tự file nguồn bằng bộ đệm 64KB để sinh ra mã băm SHA-256 (32 byte) chuẩn mực ban đầu. |
| 4 | `ClientHandler` | `SessionManager.createUploadSession(displayName: String, fileSize: long, fileHash: byte[], connectionId: String)` | `displayName: String`, `fileSize: long`, `fileHash: byte[]`, `connectionId: String` | Khởi tạo phiên quản lý tải lên mới trong bộ nhớ RAM của Server. |
| 5 | `SessionManager` | `FileManager.reserveSpace(bytes: long)` | `bytes: long` = `fileSize` | **Giải thuật CAS không khóa:** Dùng `AtomicLong.compareAndSet` kiểm tra `usedBytes + reservedBytes + bytes <= 25GB`. Dành chỗ trước dung lượng ảo mà không dùng bất kỳ ổ khóa `synchronized` nào. |
| 6 | `SessionManager` | `FileStorage.openTempFile(uploadId: String)` | `uploadId: String` | Tạo tệp vật lý `<uploadId>.tmp` rỗng trong thư mục `./storage`. |
| 7 | `TransferClient` | `ResumeManager.saveUploadState(state: UploadState)` | `state: UploadState(uploadId: String, resumeToken: String, localFilePath: String, fileSize: long, fileHashHex: String, lastOffset: long, updatedAtMs: long)` | Ghi trạng thái khởi tạo xuống thư mục `./client-state` trên đĩa client để phòng ngừa mất điện. |
| 8 | `RandomAccessFile` | `RandomAccessFile.readFully(b: byte[], off: int, len: int)` | `b: byte[]`, `off: int` = 0, `len: int` = 65536 | Đọc một khối dữ liệu 64 KiB từ file nguồn trên đĩa máy client. |
| 9 | `UploadManager` | `RandomAccessFile.seek(pos: long)` & `RandomAccessFile.write(b: byte[], off: int, len: int)` | `pos: long` = `offset`, `b: byte[]` = `data`, `off: int` = 0, `len: int` = `length` | Ghi dữ liệu trực tiếp vào tệp tạm trên đĩa server. |
| 10 | `UploadManager` | `TransferSession.updateDigest(data: byte[], offset: int, length: int)` | `data: byte[]`, `offset: int` = 0, `length: int` | **Streaming Hash:** Đẩy mảng byte vào `MessageDigest.update()`. Trạng thái băm được tích lũy liên tục trong RAM, không cần đọc lại đĩa sau này. |
| 11 | `UploadManager` | `UploadManager.finishUpload()` | Không | Bước chốt hoàn tất khi nhận `UPLOAD_COMMIT`. |
| 12 | `TransferSession` | `MessageDigest.digest()` | Không | Lấy ra 32 byte băm SHA-256 cuối cùng trực tiếp từ bộ nhớ RAM tức thì, loại bỏ hoàn toàn việc phải đọc lại toàn bộ file trên đĩa cứng. |
| 13 | `UploadManager` | `HashUtils.matches(hashA: byte[], hashB: byte[])` | `hashA: byte[]` = `finalHash`, `hashB: byte[]` = `sourceHash` | Xác thực tính toàn vẹn 100%. Nếu sai: xóa file tạm, hoàn trả quota và quăng lỗi 422. |
| 14 | `UploadManager` | `FileStorage.saveCompletedFile(uploadId: String, fileId: String)` | `uploadId: String`, `fileId: String` | Gọi `Files.move(..., StandardCopyOption.ATOMIC_MOVE)` đổi tên nguyên tử `<uploadId>.tmp` thành `<fileId>.dat`. |
| 15 | `UploadManager` | `FileManager.confirmSpace(bytes: long)` | `bytes: long` = `session.getFileSize()` | Trừ dung lượng ở `reservedBytes` và cộng vào `usedBytes`. |
| 16 | `TransferClient` | `ResumeManager.removeUploadState(localFilePath: String)` | `localFilePath: String` = `file.getAbsolutePath()` | Xóa file `.state` trên máy client vì file đã truyền xong trọn vẹn, không cần resume nữa. |

---

## USE CASE 06: TẠM DỪNG, TIẾP TỤC VÀ HỦY BỎ LƯỢT TRUYỀN TẢI

### 1. Ý nghĩa & Bối cảnh
Khi đang truyền một file 3.79 GB, người dùng muốn gõ lệnh `pause` để nhường băng thông cho việc khác, gõ `resume` để chạy tiếp, hoặc gõ `cancel` để hủy. Nhờ kiến trúc tách rời 2 luồng độc lập, giao diện bàn phím không bao giờ bị đóng băng.

### 2. Sơ đồ tương tác 2 luồng phía Client

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as Luồng 1: ConsoleUI (Main Thread)
    participant TC as Luồng 2: TransferClient (Worker Thread)
    participant Srv as Server

    Note over UI,TC: File lớn đang được truyền tuần tự từng chunk 64 KiB
    TC->>Srv: UPLOAD_CHUNK
    Srv-->>TC: 200 OK
    User->>UI: Gõ lệnh "pause"
    UI->>TC: pauseTransfer() -> Đặt cờ: volatile boolean isPaused = true
    Note over TC: Vòng lặp phát hiện isPaused == true -> Dừng đọc đĩa & ngừng gửi chunk
    TC->>TC: Đi vào vòng lặp Thread.sleep(200) chờ cờ
    UI->>User: Thông báo: "Đã gửi lệnh TẠM DỪNG (PAUSE) lượt truyền."
    
    User->>UI: Sau 5 phút, gõ lệnh "resume"
    UI->>TC: resumeTransfer() -> Đặt cờ: volatile boolean isPaused = false
    Note over TC: Thoát khỏi vòng lặp ngủ, tiếp tục đọc đĩa và bơm chunk tiếp theo!
    TC->>Srv: UPLOAD_CHUNK tiếp theo
    Srv-->>TC: 200 OK

    User->>UI: Nếu gõ lệnh "cancel"
    UI->>TC: cancelTransfer() -> Đặt cờ: volatile boolean isCancelled = true
    Note over TC: Thoát khỏi vòng lặp truyền file ngay lập tức
    TC->>TC: ResumeManager.removeUploadState() -> Xóa checkpoint
    TC-->>UI: Trả về kết quả: Lượt truyền đã bị hủy bởi người dùng
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ConsoleUI` | `ConsoleUI.handlePause()` | Không | Bắt lệnh bàn phím `pause` từ luồng chính `Main Thread`. |
| 2 | `ConsoleUI` | `TransferClient.pauseTransfer()` | Không | Thay đổi giá trị biến cờ hiệu nguyên tử `volatile boolean isPaused = true`. |
| 3 | `Thread` | `Thread.sleep(millis: long)` | `millis: long` = 200 | Luồng mạng tạm thời ngưng đẩy dữ liệu ra socket, nhường CPU và băng thông mạng. |
| 4 | `ConsoleUI` | `TransferClient.resumeTransfer()` | Không | Đặt `isPaused = false`, đánh thức luồng mạng tiếp tục đọc ghi. |
| 5 | `ConsoleUI` | `TransferClient.cancelTransfer()` | Không | Đặt `isCancelled = true`. Vòng lặp truyền kiểm tra thấy cờ này sẽ đóng file nguồn và hủy tiến trình. |
| 6 | `TransferClient` | `ResumeManager.removeUploadState(localFilePath: String)` | `localFilePath: String` = `file.getAbsolutePath()` | Dọn dẹp tệp trạng thái cục bộ khi người dùng chủ động chọn hủy bỏ. |

---

## USE CASE 07: KHÔI PHỤC LƯỢT TẢI LÊN KHI BỊ ĐỨT MẠNG (RESUME UPLOAD)

### 1. Ý nghĩa & Bối cảnh
Khi đang upload dở (ví dụ được 1.5 GB / 3.79 GB) thì bị tuột dây mạng LAN hoặc người dùng tắt máy client. File tạm trên server vẫn còn nguyên và được bảo lưu trong 30 phút. Client cắm lại mạng và tiếp tục truyền đúng từ byte 1.5 GB mà không phải upload lại từ byte 0.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as ConsoleUI
    participant TC as TransferClient
    participant RM as ResumeManager
    participant CH as ClientHandler
    participant SM as SessionManager
    participant FS as FileStorage
    participant Digest as HashUtils

    Note over TC,CH: Mạng LAN bị đứt đột ngột -> Socket văng lỗi IOException -> Đóng kết nối
    Note over CH: Server giữ nguyên file tạm .tmp và giữ session trong 30 phút
    Note over TC: Client lưu lại uploadId, resumeToken và lastOffset trong .client-state
    User->>UI: Cắm lại mạng, gõ lại: upload "LabtainerVM.ova"
    UI->>TC: uploadFile(file)
    TC->>RM: loadUploadState(filePath) -> Tìm thấy phiên dở cũ!
    TC->>CH: Gửi UPLOAD_RESUME (uploadId, resumeToken, fileSize, fileHash)
    CH->>SM: resumeUploadSession(uploadId, resumeToken, fileSize, fileHash, connId)
    SM->>SM: session.lock()
    SM->>SM: Kiểm tra resumeToken khớp 100% -> Chống chiếm đoạt phiên!
    SM->>FS: getTempFileSize(uploadId) -> Đọc con trỏ đĩa thực tế: 1,500,000,000 bytes
    SM->>Digest: resumeSha256State(tempFile, 1500000000L) -> Nạp lại trạng thái băm từ đĩa
    SM->>SM: session.setCurrentOffset(1500000000L) & session.assignConnection(connId)
    SM-->>CH: Trả về TransferSession đã khôi phục
    CH-->>TC: Trả về 200 OK (uploadId, resumeToken, nextOffset = 1500000000)
    Note over TC: Client tua con trỏ file nguồn: raf.seek(1500000000L)
    loop Tiếp tục truyền từ byte 1,500,000,001 đến hết
        TC->>CH: UPLOAD_CHUNK (offset = 1500000000, ...)
    end
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `TransferClient` | `ResumeManager.loadUploadState(localFilePath: String)` | `localFilePath: String` = `filePath` | Đọc tệp `.state` trong `./client-state` để lấy lại `uploadId: String`, `resumeToken: String` và `fileHashHex: String` của lần truyền trước. |
| 2 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 17: `OP_UPLOAD_RESUME`, reqId: long, status = 0, payload: `[uploadId, resumeToken, fileSize, fileHash]`) | Gửi yêu cầu xin nối tiếp lượt truyền tải dở sang server. |
| 3 | `ClientHandler` | `SessionManager.resumeUploadSession(uploadId: String, resumeToken: String, fileSize: long, fileHash: byte[], connectionId: String)` | `uploadId: String`, `resumeToken: String`, `fileSize: long`, `fileHash: byte[]`, `connectionId: String` | Kiểm tra tính hợp lệ của phiên dở trong RAM server. |
| 4 | `String` | `String.equals(anObject: Object)` | `anObject: Object` = `resumeToken: String` | **Cơ chế an ninh:** Bắt buộc token phải khớp để ngăn kẻ xấu trong LAN gửi bừa `uploadId` nhằm phá hoại file của người khác (lỗi 403). |
| 5 | `SessionManager` | `FileStorage.getTempFileSize(uploadId: String)` | `uploadId: String` | **Nguyên tắc an toàn:** Không tin lời khai của client. Server tự kiểm tra kích thước file tạm thực tế đã ghi chắc chắn xuống đĩa (`RandomAccessFile.length()`). |
| 6 | `SessionManager` | `HashUtils.resumeSha256State(file: File, limitOffset: long)` | `file: File` = `tempFile`, `limitOffset: long` = `actualDiskSize` | Đọc file tạm từ byte 0 đến vị trí byte đĩa hiện tại để nạp lại trạng thái thanh ghi nội bộ của thuật toán SHA-256. |
| 7 | `ClientHandler` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 17, reqId: long, status = 200, body: `[uploadId, resumeToken, nextOffset: long, chunkSize, state, fileId]`) | Báo cho client biết vị trí byte server đang chờ nhận tiếp. |
| 8 | `RandomAccessFile` | `RandomAccessFile.seek(pos: long)` | `pos: long` = `nextOffset` | Client tua con trỏ đọc của file nguồn trên máy mình đến đúng byte server yêu cầu và tiếp tục gửi chunk tiếp theo. |

---

## USE CASE 08: TẢI VỀ TỆP & XÁC MINH TÍNH TOÀN VẸN (DOWNLOAD FILE)

### 1. Ý nghĩa & Bối cảnh
Người dùng gõ lệnh `download <fileId> [saveDir]` để tải một file từ server về máy mình. File được tải về dưới dạng file tạm `.part`. Sau khi nhận đủ 100% dữ liệu, client tự tính lại mã SHA-256 của file tải về so với mã SHA-256 gốc của server; nếu trùng khớp mới đổi tên thành file chính thức.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as ConsoleUI
    participant TC as TransferClient
    participant CH as ClientHandler
    participant FM as FileManager
    participant DM as DownloadManager
    participant FS as FileStorage
    participant LocalDisk as Đĩa cứng Client

    User->>UI: download file-7d4618ef8fe8 "C:/Downloads"
    UI->>TC: downloadFile(fileId, targetDir)
    TC->>CH: Gửi DOWNLOAD_OPEN (fileId, requestedOffset = 0)
    CH->>FM: findFile(fileId) -> Kiểm tra file có tồn tại trong danh mục
    CH->>DM: new DownloadManager(fileInfo, fileStorage) -> Mở file .dat ở chế độ "r"
    CH-->>TC: Trả về 200 OK (fileId, displayName, fileSize, fileHash, acceptedOffset=0, chunkSize)
    TC->>LocalDisk: Tạo file tạm: displayName.part
    
    loop Đọc từng chunk 64 KiB gửi về cho Client
        TC->>CH: Gửi DOWNLOAD_NEXT (offset, requestedBytes = 64KB)
        CH->>DM: readChunk(offset, requestedBytes)
        DM->>FS: raf.seek(offset) & raf.readFully(buffer) -> Đọc từ đĩa server
        DM-->>CH: Trả về mảng byte dữ liệu
        CH-->>TC: Trả về 200 OK (offset, dataLength, chunkData)
        TC->>LocalDisk: Ghi chunkData vào file displayName.part
        TC->>TC: activeProgress.update() -> Cập nhật tiến độ tải về
    end

    TC->>LocalDisk: HashUtils.calculateFileSha256(tempFile) -> Tự băm kiểm tra file tải về
    alt Mã băm SHA-256 khớp 100%
        TC->>CH: Gửi DOWNLOAD_CLOSE (verified = 1)
        CH->>DM: close() -> Đóng RandomAccessFile trên server
        TC->>LocalDisk: Đổi tên file tạm .part thành file chính thức
        TC-->>UI: Thông báo: TẢI VỀ THÀNH CÔNG!
    else Mã băm sai lệch (bị lỗi mạng)
        TC->>CH: Gửi DOWNLOAD_CLOSE (verified = 0)
        TC->>LocalDisk: Xóa file .part hỏng
        TC-->>UI: Báo lỗi mã băm 422!
    end
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 32: `OP_DOWNLOAD_OPEN`, reqId: long, status = 0, payload: `[fileId: String, requestedOffset: long]`) | Gửi yêu cầu mở lượt tải xuống kèm `requestedOffset: long` (hỗ trợ cả tải mới từ 0 hoặc tải tiếp). |
| 2 | `ClientHandler` | `FileManager.findFile(fileId: String)` | `fileId: String` | Tra cứu thông tin tệp trong danh mục bộ nhớ RAM. Nếu không có trả lỗi 404. |
| 3 | `ClientHandler` | `new DownloadManager(fileInfo: FileInfo, fileStorage: FileStorage)` | `fileInfo: FileInfo`, `fileStorage: FileStorage` | Mở một luồng đọc riêng biệt (`RandomAccessFile` chế độ `"r"`) trên tệp `<fileId>.dat`. |
| 4 | `TransferClient` | `new RandomAccessFile(file: File, mode: String)` | `file: File` = `tempFile` (`.part`), `mode: String` = `"rw"` | Mở file tạm trên máy client để ghi dữ liệu nhận về. |
| 5 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 33: `OP_DOWNLOAD_NEXT`, reqId: long, status = 0, body: `[offset: long, requestedBytes: int]`) | Kéo về một chunk dữ liệu 64 KiB tiếp theo. |
| 6 | `DownloadManager` | `DownloadManager.readChunk(offset: long, requestedBytes: int)` | `offset: long`, `requestedBytes: int` = 65536 | Đọc đúng số byte dữ liệu từ đĩa server vào mảng byte đệm. |
| 7 | `TransferClient` | `RandomAccessFile.write(b: byte[], off: int, len: int)` | `b: byte[]` = `chunkData`, `off: int` = 0, `len: int` = `dataLength` | Ghi dữ liệu trực tiếp vào file đĩa của client. |
| 8 | `TransferClient` | `HashUtils.calculateFileSha256(file: File)` | `file: File` = `tempFile` (`.part`) | Client tự chạy giải thuật SHA-256 trên tệp vừa tải về hoàn tất. |
| 9 | `TransferClient` | `HashUtils.matches(hashA: byte[], hashB: byte[])` | `hashA: byte[]` = `downloadedHash`, `hashB: byte[]` = `expectedHash` | Đối chiếu tính toàn vẹn độc lập từ phía Client. |
| 10 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 34: `OP_DOWNLOAD_CLOSE`, reqId: long, status = 0, body: `[verified: boolean]`) | Báo cho server biết kết quả xác minh để server đóng handle file đọc. |
| 11 | `DownloadManager` | `DownloadManager.close()` | Không | Đóng `RandomAccessFile` trên server, giải phóng tài nguyên hệ điều hành. |
| 12 | `File` | `File.renameTo(dest: File)` | `dest: File` = `finalFile` | Đổi tên file `.part` thành tên file chính thức ban đầu. |

---

## USE CASE 09: QUÉT DỌN ĐỊNH KỲ & THU HỒI TÀI NGUYÊN QUÁ HẠN

### 1. Ý nghĩa & Bối cảnh
Để hệ thống không bị rò rỉ tài nguyên (Resource Leak), luồng `ConnectionWatcher` chạy ngầm định kỳ mỗi 10 giây một lần để:
1. Phát hiện và ép đóng các socket TCP bị đơ (quá 60 giây không gửi dữ liệu).
2. Thu hồi các phiên upload dở bị bỏ rơi quá 30 phút: xóa file `.tmp` trên đĩa và hoàn trả dung lượng hạn ngạch Quota cho server.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    participant CW as ConnectionWatcher (Daemon Thread)
    participant CL as ConnectionListener
    participant CH as ClientHandler
    participant SM as SessionManager
    participant FS as FileStorage
    participant FM as FileManager

    Note over CW: Ngủ 10 giây giữa mỗi chu kỳ quét
    CW->>CL: checkClientTimeouts()
    loop Duyệt từng kết nối trong activeClients
        CL->>CL: Kiểm tra: (now - handler.getLastActiveTimeMs()) > 60,000ms
        opt Quá hạn 60 giây không có byte nào
            CL->>CH: close() -> Ép đóng Socket và dọn worker thread
        end
    end

    CW->>SM: removeExpiredSessions()
    SM->>SM: Tạo Snapshot: lọc các session dở có (now - lastActiveEpochMs) > 30 phút
    loop Duyệt danh sách session hết hạn
        SM->>SM: sessions.remove(uploadId) -> Xóa khỏi RAM
        SM->>FS: deleteTempFile(uploadId) -> Xóa file .tmp trên đĩa cứng
        SM->>FM: releaseReservedSpace(fileSize) -> Hoàn trả Quota ảo bằng CAS
    end
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ConnectionWatcher` | `Thread.sleep(millis: long)` | `millis: long` = 10000 | Luồng ngủ 10 giây để không gây tốn CPU. |
| 2 | `ConnectionWatcher` | `ConnectionListener.checkClientTimeouts()` | Không | Yêu cầu kiểm tra toàn bộ danh sách kết nối client đang phục vụ. |
| 3 | `ClientHandler` | `ClientHandler.getLastActiveTimeMs()` | Không | Lấy mốc thời gian hoạt động cuối cùng để kiểm tra điều kiện `(now: long - lastActiveTimeMs: long) > 60000L`. |
| 4 | `ConnectionListener` | `ClientHandler.close()` | Không | Đóng socket, hủy `UploadManager`/`DownloadManager`, giải phóng worker thread về Thread Pool. |
| 5 | `ConnectionWatcher` | `SessionManager.removeExpiredSessions()` | Không | Yêu cầu quét bảng session tải lên dở trong RAM. |
| 6 | `SessionManager` | `TransferSession.getLastActiveEpochMs()` | Không | Kiểm tra điều kiện `(now: long - lastActiveEpochMs: long) > Protocol.INACTIVE_UPLOAD_TTL_MS (30 phút)` để trích xuất `expiredList: List<TransferSession>`. |
| 7 | `SessionManager` | `FileStorage.deleteTempFile(uploadId: String)` | `uploadId: String` | Xóa tệp vật lý `.tmp` đang ghi dở trên đĩa cứng để giải phóng dung lượng ổ cứng. |
| 8 | `SessionManager` | `FileManager.releaseReservedSpace(bytes: long)` | `bytes: long` = `session.getFileSize()` | **Hoàn trả Quota:** Gọi `reservedBytes.addAndGet(-bytes)` để trả lại dung lượng ảo cho server, cho phép người khác tải file lên. |

---

## USE CASE 10: ĐÓNG KẾT NỐI LỊCH SỰ & TẮT AN TOÀN SERVER (GRACEFUL SHUTDOWN)

### 1. Ý nghĩa & Bối cảnh
Khi người dùng gõ `exit` ở client hoặc quản trị viên bấm `Ctrl + C` ở server, hệ thống phải giải phóng tài nguyên một cách lịch sự (Graceful), đóng socket, dừng thread pool mà không làm hỏng dữ liệu đang ghi.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng Client
    participant UI as ConsoleUI
    participant TC as TransferClient
    participant CH as ClientHandler
    participant Socket as TCP Socket

    User->>UI: Gõ lệnh "exit" hoặc "quit"
    UI->>TC: close()
    TC->>CH: Gửi QUIT (opcode 3, body rỗng)
    CH-->>TC: Phản hồi 200 OK
    TC->>Socket: socket.close() -> Đóng socket phía client
    CH->>CH: close() -> Đóng socket phía server, xóa khỏi activeClients
    UI->>UI: isRunning = false -> Dừng tiến trình Client an toàn
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `ConsoleUI` | `ConsoleUI.handleExit()` | Không | Tiếp nhận lệnh `exit` từ bàn phím. |
| 2 | `TransferClient` | `MessageIO.writeMessage(msg: Message)` | `msg: Message` (opcode = 3: `OP_QUIT`, reqId: long, status = 0, body = `byte[0]`) | Gửi thông báo lịch sự cho server biết mình chủ động ngắt kết nối. |
| 3 | `ClientHandler` | `ClientHandler.handleQuit(req: Message)` | `req: Message` | Trả lời 200 OK và chuyển trạng thái `this.state = State.CLOSING`. |
| 4 | `TransferClient` | `Socket.close()` | Không | Đóng kết nối TCP Socket từ phía client. |
| 5 | `ClientHandler` | `ClientHandler.close()` | Không | Đóng socket, xóa handler khỏi `activeClients: List<ClientHandler>`, hoàn trả worker thread về Thread Pool. |
| 6 | `ServerMain` | `Runtime.addShutdownHook(hook: Thread)` | `hook: Thread` | Đăng ký luồng dừng hệ thống khi server bị tắt bằng `Ctrl + C`: gọi `connectionWatcher.stop()`, `discoveryServer.stop()`, `connectionListener.stop()`, đóng `ServerSocket` và `threadPool.shutdownNow()`. |

---

## USE CASE 11: KIỂM SOÁT CHẶN XUNG ĐỘT TRUYỀN TẢI TRÊN CÙNG MỘT CLIENT (SINGLE-CLIENT MUTUAL EXCLUSION)

### 1. Ý nghĩa & Bối cảnh
Trong khi một tác vụ truyền tải ngầm đang thực thi (ví dụ người dùng đang `upload fileA` hoặc đang `download fileB`), người dùng lại vô tình hoặc cố ý nhập thêm một lệnh truyền tải khác (`download fileB` hoặc `upload fileA`) trên cùng một cửa sổ Console UI.
Để bảo vệ tính toàn vẹn của kết nối TCP Socket đơn (không hỗ trợ Multiplexing) và tránh xung đột biến trạng thái, hệ thống kích hoạt **cơ chế bảo vệ 4 tầng** để từ chối ngay lập tức lệnh mới, cảnh báo người dùng chờ đợi hoặc hủy tác vụ hiện tại, đảm bảo tác vụ đang chạy không bị gián đoạn.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant UI as ConsoleUI (Main Thread)
    participant Exec as transferExecutor (SingleThreadExecutor)
    participant TC as TransferClient (Worker Thread)
    participant Srv as Server (ClientHandler)

    Note over Exec,TC: Tác vụ 1: Đang truyền fileA ngầm (activeTransferTask != done)
    TC->>Srv: Bơm các chunk 64 KiB liên tục qua Socket TCP
    User->>UI: Người dùng gõ tiếp: "download fileB" (hoặc "upload fileC")
    UI->>UI: Kiểm tra: activeTransferTask != null && !activeTransferTask.isDone()
    Note over UI: PHÁT HIỆN XUNG ĐỘT TRUYỀN TẢI ĐỒNG THỜI!
    UI-->>User: In cảnh báo: "Đang có một lượt truyền khác đang chạy! Hãy đợi hoặc gõ 'cancel'."
    Note over UI: Lệnh mới bị TỪ CHỐI NGAY, không được submit vào hàng đợi
    Note over Exec,TC: Tác vụ 1 (fileA) vẫn tiếp tục truyền mượt mà 100%!
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | Người dùng | `ConsoleUI.handleUpload(fullLine: String)` / `handleDownload(fullLine: String)` | `fullLine: String` = Chuỗi lệnh nhập từ bàn phím | Tiếp nhận lệnh truyền tải thứ hai khi lệnh thứ nhất đang thực thi. |
| 2 | `ConsoleUI` | `Future.isDone()` | Không | Kiểm tra trên `activeTransferTask: Future<?>` xem luồng worker truyền ngầm trước đó đã hoàn tất hay chưa. |
| 3 | `ConsoleUI` | `System.out.println(x: String)` | `x: String` = Thông báo cảnh báo | In thông báo từ chối và thoát khỏi hàm ngay lập tức (`return`), ngăn chặn việc tạo thêm task mới. |
| 4 | `ExecutorService` | `ExecutorService.submit(task: Runnable)` | `task: Runnable` | Đẩy tác vụ truyền tải vào `transferExecutor: ExecutorService` (chỉ được gọi khi `activeTransferTask == null` hoặc đã `isDone()`). |
| 5 | `TransferClient` | `synchronized uploadFile(file: File)` / `synchronized downloadFile(fileId: String, targetDir: File)` | `file: File` / `fileId: String`, `targetDir: File` | **Chốt chặn an toàn tầng 2:** Dù có luồng nào vô tình vượt qua UI thì từ khóa `synchronized` trên `TransferClient` vẫn chặn luồng sau đứng chờ luồng trước kết thúc. |
| 6 | `MessageIO` | `readMessage()` / `writeMessage(msg: Message)` | `msg: Message` | **Chốt chặn an toàn tầng 3:** Bảo vệ đường truyền byte stream nhị phân khỏi nguy cơ đan xen dữ liệu (Interleaving / Corrupted Framing). |
| 7 | `ClientHandler` | Gán `this.state: State` | `state: State` = `State.UPLOADING` / `State.DOWNLOADING` | **Chốt chặn an toàn tầng 4:** Server chỉ chấp nhận 1 nghiệp vụ truyền tải tại 1 thời điểm trên 1 kết nối TCP. |

### 4. Cơ chế Bảo vệ 4 Tầng chống Xung đột (4-Layer Protection)
1. **Tầng 1 (Giao diện CLI):** `ConsoleUI` sử dụng `Executors.newSingleThreadExecutor()` và cờ `activeTransferTask.isDone()` để khống chế chỉ 1 tác vụ truyền dữ liệu được phép chạy ngầm tại một thời điểm.
2. **Tầng 2 (Lõi Xử lý Client):** Các phương thức `uploadFile()` và `downloadFile()` trong `TransferClient` đều là `synchronized`, dùng chung monitor lock `this` để ngăn chặn đa luồng truy cập đồng thời.
3. **Tầng 3 (Giao thức Mạng):** LFTP/1 là giao thức tuần tự (Half-duplex Request-Response) trên 1 kết nối Socket TCP. Việc cấm truyền đồng thời giúp các chunk nhị phân không bị trộn lẫn trên dòng byte TCP (`TCP stream`).
4. **Tầng 4 (Máy trạng thái Server):** `ClientHandler` quản lý trạng thái kết nối độc quyền: hoặc là `UPLOADING` hoặc là `DOWNLOADING`, từ chối bất kỳ gói tin nào không đúng ngữ cảnh hiện tại.

---

## USE CASE 12: TRUYỀN TẢI SONG SONG ĐA CLIENT (MULTI-CLIENT CONCURRENT TRANSFERS)

### 1. Ý nghĩa & Bối cảnh
Trong môi trường mạng nội bộ (LAN), nhiều máy trạm khác nhau (hoặc cùng một máy tính mở 2 cửa sổ terminal độc lập: Client 1 và Client 2) thực hiện truyền file đồng thời: Client 1 `upload fileA` lên server, cùng lúc Client 2 `download fileB` về máy.
Server phải xử lý song song, tối ưu hóa I/O đĩa cứng (1 luồng ghi file tạm `.tmp`, 1 luồng đọc file chính thức `.dat`) và tận dụng tối đa băng thông mạng LAN Full-Duplex mà không xảy ra tranh chấp khóa hay nghẽn cổ chai.

### 2. Sơ đồ tuần tự thực thi

```mermaid
sequenceDiagram
    autonumber
    actor C1 as Client 1 (Terminal 1)
    actor C2 as Client 2 (Terminal 2)
    participant Listener as ConnectionListener (Port 5000)
    participant Pool as ThreadPoolExecutor (20 Workers)
    participant H1 as ClientHandler 1 (Worker 1)
    participant H2 as ClientHandler 2 (Worker 2)
    participant UM as UploadManager 1
    participant DM as DownloadManager 2
    participant Disk as FileStorage (Ổ đĩa Server)

    C1->>Listener: Kết nối TCP Socket 1 (Cổng 5000)
    Listener->>Pool: Giao Socket 1 cho Worker Thread 1
    Pool->>H1: run() -> Bắt tay HELLO -> State: READY
    
    C2->>Listener: Kết nối TCP Socket 2 (Cổng 5000)
    Listener->>Pool: Giao Socket 2 cho Worker Thread 2
    Pool->>H2: run() -> Bắt tay HELLO -> State: READY

    par Xử lý Song song Độc lập trên 2 Nhân CPU
        Note over C1,H1: Client 1 thực hiện UPLOAD fileA
        C1->>H1: OP_UPLOAD_INIT & OP_UPLOAD_CHUNK (fileA)
        H1->>UM: writeChunk(offset, data)
        UM->>Disk: raf.write(...) vào fileA.tmp (Chế độ "rw")
        H1-->>C1: 200 OK (nextOffset)
    and
        Note over C2,H2: Client 2 thực hiện DOWNLOAD fileB
        C2->>H2: OP_DOWNLOAD_OPEN & OP_DOWNLOAD_NEXT (fileB)
        H2->>DM: readChunk(offset, requestedBytes)
        DM->>Disk: raf.read(...) từ fileB.dat (Chế độ "r")
        H2-->>C2: 200 OK (chunkData)
    end

    Note over Disk: Ghi file tạm và Đọc file chính thức hoàn toàn độc lập, không khóa nhau!
```

### 3. Bảng phân tích chi tiết

| Thứ tự | Lớp gọi | Phương thức được gọi | Tham số truyền vào (Tên biến: Kiểu dữ liệu) | Mục đích kỹ thuật |
|:---:|---|---|---|---|
| 1 | `Client 1 & 2` | `Socket.connect(endpoint: SocketAddress)` | `endpoint: SocketAddress` = `InetSocketAddress(host: String, port: int)` | Hai client tạo 2 kết nối TCP Socket độc lập trên hệ điều hành tới máy chủ. |
| 2 | `ConnectionListener` | `ServerSocket.accept()` | Không | Đón nhận từng socket kết nối đến, kiểm tra số kết nối đang phục vụ `activeClients.size() < MAX_CLIENTS (20)`. |
| 3 | `ThreadPoolExecutor` | `ThreadPoolExecutor.execute(command: Runnable)` | `command: Runnable` = `new ClientHandler(socket: Socket, ...)` | Bàn giao từng socket cho 1 Worker Thread riêng biệt trong pool xử lý song song. |
| 4 | `ClientHandler 1` | `handleUploadInit(req: Message)` $\rightarrow$ `new UploadManager(session: TransferSession, fileStorage: FileStorage, fileManager: FileManager)` | `req: Message`, `session: TransferSession`, `fileStorage: FileStorage`, `fileManager: FileManager` | Thiết lập trạng thái `UPLOADING`, mở file tạm `uploadId.tmp` trên đĩa ở chế độ đọc/ghi (`"rw"`). |
| 5 | `ClientHandler 2` | `handleDownloadOpen(req: Message)` $\rightarrow$ `new DownloadManager(fileInfo: FileInfo, fileStorage: FileStorage)` | `req: Message`, `fileInfo: FileInfo`, `fileStorage: FileStorage` | Thiết lập trạng thái `DOWNLOADING`, mở file hoàn tất `fileId.dat` trên đĩa ở chế độ chỉ đọc (`"r"`). |
| 6 | `FileManager` | `FileManager.reserveSpace(bytes: long)` | `bytes: long` = `session.getFileSize()` | Client 1 thực hiện CAS Lock-free trên biến nguyên tử `AtomicLong` mà không hề khóa toàn bộ server, không làm chậm Client 2. |
| 7 | `FileStorage` | `openTempFile(uploadId: String)` & `openForDownload(fileId: String)` | `uploadId: String`, `fileId: String` | Mở 2 đối tượng `RandomAccessFile` riêng biệt trỏ tới 2 file khác nhau trên hệ thống tệp đĩa cứng. |
| 8 | Card mạng NIC | Giao tiếp mạng Full-Duplex | `txData: byte[]`, `rxData: byte[]` | Kênh truyền tải lên (Tx) của Client 1 và kênh tải xuống (Rx) của Client 2 diễn ra trên 2 luồng dây dẫn vật lý riêng biệt, đạt tốc độ dây tối đa (~100 MB/s mỗi chiều). |

### 4. Đánh giá Tính Độc lập & Tối ưu Hiệu năng
* **Không tranh chấp khóa (Lock Contention = 0):** Client 1 giữ khóa của `TransferSession 1` (Upload), Client 2 đọc dữ liệu từ `DownloadManager 2` độc lập. Hai luồng không hề dùng chung bất kỳ ổ khóa nào.
* **Tối ưu hóa I/O đĩa cứng:** Thao tác ghi file tạm `.tmp` sử dụng vùng đệm tuần tự, thao tác đọc file `.dat` được bộ đệm Page Cache của Hệ điều hành tối ưu, giảm thiểu tối đa hiện tượng phân mảnh đầu đọc đĩa.
* **Tận dụng tối đa phần cứng:** Hai luồng Worker chạy trên 2 CPU Core khác nhau, phục vụ người dùng trơn tru mà không làm sụt giảm băng thông truyền tải của nhau.

---

# TỔNG KẾT BẢNG ÁNH XẠ KIẾN TRÚC TỔNG THỂ

```text
+----------------------------------------------------------------------------------------------------+
|                                    TỔNG THỂ HỆ THỐNG LFTP/1                                        |
+----------------------------------------------------------------------------------------------------+
|                                                                                                    |
|  [PHÍA CLIENT]                                                     [PHÍA SERVER]                   |
|                                                                                                    |
|  ConsoleUI (Luồng 1 - Main)                                        ServerMain                      |
|      │ (Nhập lệnh, pause, resume)                                      │ (Bootstrap & Dọn rác .tmp) |
|      ▼                                                                 ▼                           |
|  TransferClient (Luồng 2 - Worker)                                 ConnectionListener              |
|      │ (Bơm chunk 64KB, tính tốc độ MB/s)                             │ (ServerSocket 5000)        |
|      │                                                                 ▼                           |
|      ├──► ResumeManager (Lưu state đĩa .client-state)               ThreadPoolExecutor (20 luồng)  |
|      ├──► HashUtils (Băm SHA-256 nguồn)                                │ (Worker Task)             |
|      │                                                                 ▼                           |
|      ▼                                                             ClientHandler                   |
|  MessageIO (Client)                                                    │ (State Machine, Điều phối)|
|      │                                                                 ├──► MessageIO (Server)     |
|      │          KÊNH TRUYỀN TCP SOCKET (CỔNG 5000)                     ├──► UploadManager          |
|      └────────────────────────────────────────────────────────────────►│      │ (Ghi đĩa & băm RAM)|
|               Khung nhị phân: [frameLen][12B Header][Body]             │      ▼                    |
|                                                                        │    TransferSession        |
|                                                                        │      │ (Streaming Hash)   |
|                                                                        ├──► DownloadManager        |
|                                                                        │      │ (Đọc file .dat)    |
|                                                                        ├──► SessionManager         |
|                                                                        │      │ (ConcurrentHashMap)|
|                                                                        ├──► FileManager            |
|                                                                        │      │ (CAS Quota 25GB)   |
|                                                                        └──► FileStorage            |
|                                                                               │ (Disk I/O ./storage|
|                                                                                                    |
|  [DỊCH VỤ TÌM KIẾM MẠNG NỘI BỘ]                                                                    |
|  DiscoveryClient  ───────── Gói tin UDP Broadcast (Cổng 5001) ────────► DiscoveryServer            |
|                                                                                                    |
|  [CẢNH SÁT QUÉT TIMEOUT NGẦM]                                                                      |
|  ConnectionWatcher ──────── Quét định kỳ 10 giây một lần ─────────────► Dọn Socket đơ & Quota dở   |
+----------------------------------------------------------------------------------------------------+
```

---

> [!TIP]
> Tài liệu này được biên soạn chuẩn xác 100% khớp với từng dòng mã nguồn trong thư mục [`codeV2/src/lftp/`](file:///d:/project/BTL%20LTM/codeV2/src/lftp). Khi đi thi vấn đáp hoặc bảo vệ trước giảng viên, bạn hoàn toàn có thể mở file này ra để giải trình mạch lạc bất kỳ câu hỏi nào về luồng thực thi của đồ án!
