# HƯỚNG DẪN HỌC & BẢO VỆ ĐỒ ÁN CHO BÙI VĂN ĐẠT (B23DCCN126)
## VAI TRÒ: PHỤ TRÁCH GIAO THỨC LFTP/1, MESSAGE IO, SESSION & STREAMING HASH
**Môn học:** Lập trình mạng (INT1433) · PTIT  
**Giảng viên:** TS. Đặng Ngọc Hùng  

---

# 1. TỔNG QUAN PHẠM VI TRÁCH NHIỆM

Bạn là người chịu trách nhiệm về **"ngôn ngữ giao tiếp & tính toàn vẹn dữ liệu"** của toàn bộ hệ thống:
1. Thiết kế đặc tả giao thức nhị phân LFTP/1 (12-byte Header, Opcodes, Status Codes, Binary Framing).
2. Xây dựng tầng đóng gói và bóc tách thông điệp mạng [`MessageIO`](file:///d:/project/BTL%20LTM/int1433-05-20261-grp-int1433-05-0017/server/src/lftp/common/MessageIO.java) (giải quyết lỗi dính gói TCP bằng tiền tố độ dài 4 byte).
3. Quản lý các phiên truyền tải dữ liệu (`SessionManager`, `TransferSession`), bảo mật bằng mã ngẫu nhiên 128-bit `resumeToken` và chống xung đột ghi đè bằng `activeConnectionId`.
4. Giải thuật mật mã học **băm lũy tiến SHA-256 (Streaming Hash)** theo từng chunk 64 KiB, xác thực toàn vẹn tức thì trong RAM và nạp lại băm khi phục hồi (`resumeSha256State`).
5. Cơ chế lưu vết hoàn tất 24 giờ giải quyết sự cố mất mạng ở vạch đích (**Idempotency**).

---

# 2. CÁC TÀI LIỆU BẠN BẮT BUỘC PHẢI ĐỌC TRONG `TMP/`

1. **[`tmp/protocol-design.md`](file:///d:/project/BTL%20LTM/tmp/protocol-design.md):**
   - **Mục 2:** Cơ chế Binary Framing tiền tố độ dài 4 byte (`Length-prefixed`).
   - **Mục 3:** Cấu trúc 12-byte Header cố định và quy định đọc ghi Big-Endian, chuỗi UTF-8.
   - **Mục 4:** Danh mục mã lệnh (Opcodes: `HELLO(1)`, `LIST_FILES(2)`, `UPLOAD_INIT(16)`, `UPLOAD_RESUME(17)`, `UPLOAD_CHUNK(18)`, `UPLOAD_COMMIT(19)`, `DOWNLOAD_OPEN(32)`, `DOWNLOAD_NEXT(33)`, `DOWNLOAD_CLOSE(34)`, `QUIT(3)`).
   - **Mục 5:** Các mã trạng thái (200 OK, 201 Created, 400 Bad Request, 403 Forbidden, 409 Conflict, 422 Hash Mismatch...).
   - **Mục 6:** Sơ đồ tuần tự Sequence Diagrams cho Upload, Resume và Download.
   - **Mục 8:** Lý do thiết kế (Vì sao dùng TCP, vì sao không dùng `writeUTF`, vì sao Streaming Hash).
2. **[`tmp/note.md`](file:///d:/project/BTL%20LTM/tmp/note.md):**
   - **Mục 8:** Phân biệt `TransferSession` cho Upload (Stateful) và Download (Stateless stream).
   - **Mục 9:** Toàn bộ nguyên lý Streaming Hash SHA-256 và tại sao cần đồng bộ lại băm khi Resume.
   - **Mục 11:** Bản chất của `requestId` (phạm vi Connection-Scope, ghép cặp Yêu cầu - Phản hồi).
   - **Mục 12:** Cơ chế lưu vết hoàn tất 24 giờ và tính lũy đẳng (Idempotency).
   - **Mục 13:** Chuỗi xếp tầng Stream (Decorator Pattern) trong `MessageIO`.
3. **[`tmp/key-word.md`](file:///d:/project/BTL%20LTM/tmp/key-word.md):**
   - Học thuộc các từ khóa của bạn: `Binary Framing`, `Streaming Hash SHA-256`, `Checkpoint & Resume`, `resumeToken`, `Idempotency & Giữ kết quả 24h`, `Chuỗi UTF-8 chuẩn`.

---

# 3. CÁC CLASS MÃ NGUỒN BẠN PHẢI NẮM NHƯ LÒNG BÀN TAY

Code của bạn nằm ở 2 vị trí: [`shared/src/lftp/common/`](file:///d:/project/BTL%20LTM/int1433-05-20261-grp-int1433-05-0017/shared/src/lftp/common/) và [`server/src/lftp/server/`](file:///d:/project/BTL%20LTM/int1433-05-20261-grp-int1433-05-0017/server/src/lftp/server/)

### 3.1. `Message.java` & `MessageIO.java` (Đóng gói khung mạng)
- Khung nhị phân trên đường truyền:
  $$\text{[frameLength (4B)]} + \text{[version (1B)][opcode (1B)][requestId (8B)][status (2B)]} + \text{[body]}$$
- **Chuỗi Stream:** `Socket` $\rightarrow$ `BufferedStream(64 KiB)` $\rightarrow$ `DataStream (Big-Endian)`.
- **`writeMessage()`:** Ghi 4 byte `dos.writeInt(frameLength)` $\rightarrow$ ghi 12 byte Header $\rightarrow$ ghi mảng byte `body` $\rightarrow$ gọi `dos.flush()`.
- **`readMessage()`:** Đọc 4 byte độ dài `frameLength` trước (kiểm tra $12 \le \text{len} \le 128\text{ KiB}$) $\rightarrow$ đọc 12 byte Header $\rightarrow$ đọc đúng số byte body còn lại bằng `dis.readFully(body)`.
- **`writeString()` & `readString()`:** Ghi 4 byte độ dài `int` + mảng byte UTF-8 chuẩn (khắc phục giới hạn 64 KiB của hàm `writeUTF()` có sẵn trong Java).

### 3.2. `SessionManager.java` & `TransferSession.java` (Quản lý phiên truyền)
- **Bảng phiên:** Dùng `ConcurrentHashMap<String, TransferSession> sessions` để các luồng tra cứu song song.
- **Bảo mật phiên:** Sinh mã ngẫu nhiên 128-bit `resumeToken` bằng UUID để xác thực quyền sở hữu phiên khi resume.
- **Khóa chống xung đột:** Thuộc tính `activeConnectionId`. Khi một client đang ghi chunk vào phiên, nếu client khác nhảy vào sẽ bị chặn bằng mã lỗi `STATUS_CONFLICT (409)`.
- **`removeExpiredSessions()`:** Dọn dẹp phiên dở dang quá 30 phút (`INACTIVE_UPLOAD_TTL_MS`), xóa file tạm và hoàn trả Quota.
- **Giữ lại phiên hoàn tất 24 giờ:** Nếu phiên có `uploadState == UPLOAD_STATE_COMPLETED`, không bị xóa, sẵn sàng trả lại `completedFileId` cho client nếu bị mất kết nối ở bước cuối.

### 3.3. `UploadManager.java` & `HashUtils.java` (Streaming Hash)
- **`writeChunk(offset, data, length)`:**
  1. Ghi dữ liệu vào đĩa cứng: `raf.seek(offset); raf.write(data, 0, length);`
  2. Đồng thời cập nhật băm vào RAM: `session.updateDigest(data, 0, length);` $\implies$ Gọi `sha256Digest.update()`.
- **`finishUpload()` (Lệnh COMMIT):**
  Lấy kết quả băm tức thì từ RAM: `byte[] finalHash = session.getSha256Digest().digest();`
  So khớp với `session.getFileHash()`. Nếu khớp $\implies$ đổi tên file nguyên tử và chuyển Quota; nếu sai $\implies$ xóa file tạm và ném `STATUS_HASH_MISMATCH (422)`.
- **`HashUtils.resumeSha256State(tempFile, actualDiskSize)`:**
  Đọc tuần tự từ byte 0 đến byte `actualDiskSize` của file tạm trên đĩa để nạp lại trạng thái băm vào một `MessageDigest` mới, bảo đảm đồng bộ 100% từng bit với đĩa sau sự cố đứt mạng.

---

# 4. KỊCH BẢN VẤN ĐÁP KHI THẦY HỎI (MOCK DEFENSE CHO ĐẠT)

### Câu 1: "Tại sao em phải tự thiết kế giao thức nhị phân thay vì dùng JSON hoặc Text?"
> **Trả lời:**  
> *"Dạ thưa Thầy, có 3 lý do cốt lõi:*  
> *1. **Kích thước và Băng thông:** Giao thức văn bản (như JSON) có phần tiêu đề cồng kềnh. Giao thức nhị phân của em có Header cố định đúng **12 byte**, bóc tách tức thì.*  
> *2. **Truyền dữ liệu nhị phân của file:** Nếu dùng JSON, dữ liệu byte của file bắt buộc phải mã hóa sang chuỗi Base64. Cứ 3 byte nhị phân phải mã hóa thành 4 ký tự ASCII (tỷ lệ $4/3$), làm tăng kích thước truyền tải trên dây mạng và tốn rất nhiều chu kỳ CPU để encode/decode. Giao thức nhị phân của em truyền trực tiếp mảng byte thô (`byte[]`), đọc từ đĩa và bơm thẳng ra luồng mạng, tận dụng tối đa băng thông cáp mạng LAN.*  
> *3. **Phân định ranh giới gói tin:** Tiền tố độ dài 4 byte giúp loại bỏ hoàn toàn lỗi dính gói của TCP."*

### Câu 2: "Trình bày cơ chế Streaming Hash SHA-256? Nếu mất kết nối và Resume thì mã băm được khôi phục ra sao?"
> **Trả lời:**  
> *"Dạ thưa Thầy:*  
> *- **Bình thường:** Với file 2 GiB, nếu đợi nhận xong mới mở file đọc lại từ đầu để hash thì ổ đĩa sẽ bị nghẽn I/O kéo dài, dễ làm kết nối socket bị quá hạn thời gian chờ (`SocketTimeoutException` theo mốc 60s). Em giải quyết bằng cách: mỗi chunk 64 KiB nhận được, sau khi ghi đĩa xong, em gọi ngay `sha256Digest.update(data)` trong RAM. Khi nhận lệnh `UPLOAD_COMMIT`, em chỉ việc gọi `digest()` để chốt kết quả băm tức thì ngay trong bộ nhớ RAM mà không cần đọc lại đĩa cứng.*  
> *- **Khi Resume:** `MessageDigest` trong Java là máy trạng thái 1 chiều không thể quay lui (rewind). Khi Client kết nối lại, Server lấy kích thước file tạm thực tế trên đĩa (`actualDiskSize`) làm chuẩn. Hàm `HashUtils.resumeSha256State` đọc lại từ byte 0 đến byte `actualDiskSize` để nạp vào một đối tượng `MessageDigest` mới và gán lại cho session. Sau đó các chunk tiếp theo lại được băm lũy tiến bình thường, đảm bảo mã hash cuối cùng khớp 100% từng bit với file nguồn."*

### Câu 3: "Tại sao 2 Client khác nhau có thể tạo trùng `requestId` mà không xảy ra xung đột?"
> **Trả lời:**  
> *"Dạ thưa Thầy, vì trường `requestId` có **phạm vi cục bộ trên từng kết nối TCP (Connection-Scope)**, chứ không phải phạm vi toàn cục trên toàn Server:*  
> *- Mỗi Client kết nối qua một Socket riêng biệt và được Tầng Giao vận (TCP) cách ly bằng bộ 4-tuple duy nhất `{Client IP, Client Port, Server IP, Server Port}`.*  
> *- Trên Server, mỗi kết nối do một luồng `ClientHandler` riêng phục vụ. Client A gửi `requestId = 1` thì Server phản hồi về đúng Socket A kèm `requestId = 1`. Client B gửi `requestId = 1` thì Server phản hồi về đúng Socket B kèm `requestId = 1`.*  
> *- Mục đích thực sự của `requestId` là để Client **ghép cặp Yêu cầu - Phản hồi (Correlation)** trên chính kết nối đó và chuẩn bị cho cơ chế Pipelining trong tương lai, tương tự như `Stream ID` trong chuẩn giao thức HTTP/2."*

---

# 5. CÁC CÂU HỎI MẪU ĐỂ BẠN HỎI AI TỰ HỌC & ĐÀO SÂU MÃ NGUỒN

Khi muốn AI giải thích cặn kẽ để hiểu sâu từng chi tiết giao thức và mã nguồn mình phụ trách, bạn hãy copy trực tiếp các câu lệnh (prompts) dưới đây vào AI:

### Prompt 1: Về Phân Khung Nhị Phân (Binary Framing) & Xử lý Dính Gói TCP
```text
Tôi đang phụ trách phần Thiết kế Giao thức nhị phân LFTP/1 trong đồ án Lập trình mạng Java.
Hãy đọc lớp Message.java và MessageIO.java của tôi:
1. Tại sao giao thức TCP lại là luồng byte phi ranh giới (Byte-Stream) và gây ra hiện tượng "dính gói" (Packet Concatenation) hoặc "phân mảnh gói" (Packet Fragmentation)?
2. Cơ chế tiền tố độ dài 4 byte (Length-prefixed framing) trong hàm writeMessage() và readMessage() đã giải quyết triệt để vấn đề dính gói như thế nào?
3. Tại sao trong readMessage(), tôi lại dùng dis.readFully(body) mà không dùng dis.read(body)? Sự khác biệt kỹ thuật chết người giữa 2 hàm này trong socket TCP là gì?
```

### Prompt 2: Về Chuỗi UTF-8 Chuẩn và Cái Bẫy của `writeUTF()` trong Java
```text
Trong lớp MessageIO.java, tôi đã tự định nghĩa hàm writeString(DataOutputStream out, String str) và readString(DataInputStream in).
Hãy phân tích cho tôi:
1. Tại sao tôi không dùng trực tiếp hàm DataOutputStream.writeUTF() có sẵn trong Java?
2. Hàm writeUTF() của Java sử dụng định dạng Modified UTF-8 và dùng kiểu unsigned short 2 byte để lưu độ dài. Điều này dẫn tới 2 nhược điểm chết người nào (giới hạn 65.535 byte và lỗi không tương thích với các ngôn ngữ khác như C/Python/Go)?
3. Cách tôi ghi 4 byte độ dài kiểu int (Standard Big-Endian) và mảng byte getBytes(StandardCharsets.UTF_8) đã khắc phục hoàn toàn các nhược điểm trên như thế nào?
```

### Prompt 3: Về Thuật Toán Băm Lũy Tiến (Streaming SHA-256)
```text
Hãy đọc lớp TransferSession.java và HashUtils.java:
1. Giải thích giải thuật băm lũy tiến (Streaming Hash) bằng MessageDigest.getInstance("SHA-256") trong quá trình nhận từng chunk 64 KiB (UPLOAD_CHUNK).
2. Nếu không dùng Streaming Hash mà đợi nhận xong toàn bộ file 2 GiB rồi mới mở file ra đọc để tính SHA-256 thì hệ thống sẽ gặp các vấn đề gì (nghẽn Disk I/O, độ trễ phản hồi COMMIT, nguy cơ socket bị timeout theo mốc 60s)?
3. Việc gọi sha256Digest.update(data) trực tiếp trên mảng byte vừa nhận trong bộ nhớ RAM đã giúp tối ưu hóa CPU và Disk I/O như thế nào?
```

### Prompt 4: Về Khôi Phục Trạng Thái Băm Khi Resume (`resumeSha256State`)
```text
Tôi cần hiểu sâu về cơ chế đồng bộ băm khi khôi phục truyền tải (Resume).
Trong TransferSession.java và HashUtils.java:
1. Tại sao đối tượng java.security.MessageDigest không hỗ trợ Cloneable hoặc Serialize trạng thái băm trung gian xuống đĩa cứng?
2. Khi Client rớt mạng ở vị trí 500 MB và kết nối lại bằng lệnh UPLOAD_RESUME, phương thức resumeSha256State() đã đọc lại các byte từ 0 đến 500 MB của file tạm trên đĩa để nạp lại vào MessageDigest mới như thế nào?
3. Tại sao kích thước file thực tế trên đĩa cứng (actualDiskSize) luôn được dùng làm mốc chuẩn cho offset thay vì tin tưởng hoàn toàn vào con số mà Client gửi lên?
```

### Prompt 5: Về An Toàn Phiên Truyền Tải & Tính Lũy Đẳng 24 Giờ (Idempotency)
```text
Hãy phân tích SessionManager.java và TransferSession.java:
1. Vai trò của resumeToken ngẫu nhiên 128-bit (UUID) là gì? Nếu một kẻ tấn công biết được uploadId nhưng không có resumeToken thì chúng có thể can thiệp vào phiên truyền file không?
2. Trường activeConnectionId bảo vệ phiên truyền tải chống lại lỗi ghi đè đồng thời (Concurrent Overwrite) như thế nào nếu cùng một client vô tình mở 2 kết nối gửi chunk cùng lúc?
3. Cơ chế lưu vết completedUploads trong 24 giờ hoạt động ra sao để giải quyết bài toán: Client đã upload đủ 100% dữ liệu, Server đã commit xong nhưng đường truyền mạng bị đứt trước khi Client kịp nhận gói tin ACK 200 OK?
```

---

# 6. DANH SÁCH TỪ KHÓA BẠN PHẢI NÓI KHI BẢO VỆ
Khi trả lời Thầy Hùng, hãy chủ động nhắc đến các cụm từ này để lấy trọn điểm chuyên sâu:
1. **Length-prefixed Binary Framing:** Tiền tố 4 byte độ dài cố định, giải quyết 100% hiện tượng dính gói TCP.
2. **Fixed 12-byte Big-Endian Header:** Tiêu đề nhị phân siêu nhẹ (Version, Opcode, RequestId, Status), bóc tách tức thì trong CPU.
3. **Standard UTF-8 String Encoding:** Tự xây dựng chuẩn đọc ghi chuỗi, khắc phục giới hạn 64KB của `writeUTF()`.
4. **Streaming Hash SHA-256:** Băm lũy tiến theo từng chunk 64 KiB trong RAM, xác thực toàn vẹn tức thì không cần đọc lại đĩa.
5. **Resume SHA-256 State Rebuilding:** Tái tạo trạng thái băm chính xác từ file tạm khi thực hiện Resume.
6. **128-bit Random `resumeToken`:** Mã xác thực ngẫu nhiên chống tấn công chiếm đoạt hoặc chèn dữ liệu vào phiên truyền.
7. **24-hour Idempotency Retention:** Lưu vết kết quả hoàn tất 24 giờ, đảm bảo an toàn tuyệt đối khi mất mạng ở vạch đích.


