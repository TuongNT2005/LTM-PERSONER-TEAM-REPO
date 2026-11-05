# BẢNG TỪ KHÓA ĐẶC TRƯNG & QUYẾT ĐỊNH THIẾT KẾ ĐỒ ÁN LFTP/1
## NHỮNG ĐIỂM NỔI BẬT "ĐẮT GIÁ" NHẤT ĐỂ TRẢ LỜI THẦY KHI BẢO VỆ
**Dự án:** LAN File Transfer Protocol (LFTP/1)  
**Nhóm sinh viên:** Nguyễn Trung Tường, Nguyễn Đình Khải, Bùi Văn Đạt  

---

> [!TIP]
> **Cách dùng tài liệu này:**  
> Khi Thầy hỏi: *"Bài của các em có những điểm gì nổi bật?"* hoặc *"Tại sao nhóm lại thiết kế chỗ này như thế này?"*, bạn hãy dùng ngay các **Từ khóa đinh** dưới đây để trả lời trực diện, ngắn gọn và tự tin!

---

## 1. TCP Socket (Cổng 5000)
- **Quyết định của nhóm:** Chọn **TCP Socket** cho luồng truyền file chính thay vì UDP hay HTTP REST.
- **Đặc trưng nổi bật:** Dữ liệu file đòi hỏi tính chính xác tuyệt đối. TCP cung cấp cơ chế truyền tin cậy, tự động kiểm soát luồng, bảo đảm đúng thứ tự byte và tự truyền lại gói mất. Nhóm đọc ghi mảng byte thô trực tiếp qua Socket, tận dụng tối đa băng thông mạng LAN mà không tốn phụ phí giao thức cồng kềnh.

## 2. UDP Broadcast (Cổng 5001 - Tự tìm Server)
- **Quyết định của nhóm:** Dùng gói tin **UDP Broadcast** (`255.255.255.255:5001`) thay vì bắt người dùng phải nhớ và gõ địa chỉ IP thủ công.
- **Đặc trưng nổi bật:** Client gửi gói `"LFTP_DISCOVER"`, Server phản hồi `"LFTP_OFFER <serverName> <port>"`. Client tự động nhận diện IP và kết nối TCP ngay lập tức. Nâng cao trải nghiệm người dùng trong mạng nội bộ.

## 3. Binary Framing (Khung nhị phân tiền tố độ dài)
- **Quyết định của nhóm:** Tự thiết kế **khung nhị phân** `[4 byte độ dài][12 byte Header][Body]` thay vì dùng ký tự xuống dòng `\n` hoặc JSON.
- **Đặc trưng nổi bật:**
  - Tiền tố độ dài 4 byte (`Length-prefixed`) giúp bên nhận biết chính xác kích thước khung, **triệt tiêu hoàn toàn lỗi dính gói / xé gói trong TCP (Packet Boundary Problem)**.
  - Dữ liệu file được truyền nguyên dạng byte thô, **không cần mã hóa Base64** (vốn làm tăng kích thước dữ liệu và tốn CPU encode/decode như trong JSON).

## 4. ThreadPoolExecutor (Thread Pool giới hạn 20 luồng)
- **Quyết định của nhóm:** Dùng **Thread Pool** khống chế tối đa 20 Worker Threads kết hợp hàng đợi có chặn `ArrayBlockingQueue` thay vì tạo luồng tự do (`new Thread()`).
- **Đặc trưng nổi bật:** Trong Java, mỗi thread tốn ~1 MB bộ nhớ stack hệ điều hành. Giới hạn 20 luồng giúp khống chế tổng bộ nhớ stack chỉ tốn đúng **20 MB**, bảo vệ Server an toàn tuyệt đối, tránh lỗi sập máy chủ do cạn kiệt tài nguyên (`OutOfMemoryError: unable to create new native thread`).

## 5. Mô hình Client 2 luồng (UI Thread & Worker Thread)
- **Quyết định của nhóm:** Tách riêng **luồng chính `ConsoleUI`** (nhận lệnh bàn phím) và **luồng phụ `TransferClient`** (truyền nhận mạng ngầm) thay vì chỉ dùng 1 luồng đơn.
- **Đặc trưng nổi bật:** Luồng giao diện không bao giờ bị đơ (freeze) khi đang truyền file dung lượng lớn, cho phép người dùng có thể gõ lệnh tạm dừng (`pause`), tiếp tục (`resume`) hoặc hủy bỏ (`cancel`) bất cứ lúc nào.

## 6. Từ khóa `volatile` (Đồng bộ trạng thái không cần khóa)
- **Quyết định của nhóm:** Khai báo các cờ điều khiển `isPaused`, `isCancelled` bằng từ khóa **`volatile`**.
- **Đặc trưng nổi bật:** Ép CPU đọc ghi giá trị trực tiếp từ bộ nhớ RAM chính, vô hiệu hóa bộ đệm Cache L1/L2 của vi xử lý. Giúp luồng truyền mạng ngầm nhìn thấy ngay lập tức khi luồng giao diện thay đổi cờ từ bàn phím mà không cần dùng khối khóa nặng nề.

## 7. Streaming Hash (SHA-256 lũy tiến)
- **Quyết định của nhóm:** Băm lũy tiến song song từng chunk 64 KiB bằng `MessageDigest.update()` ngay khi dữ liệu vừa chạm RAM, thay vì đợi nhận xong 2 GiB mới đọc lại file từ đầu để tính băm.
- **Đặc trưng nổi bật:** Bước chốt hoàn tất (`COMMIT`) diễn ra **tức thì ngay trong bộ nhớ RAM**, không tốn bất kỳ lượt đọc lại đĩa cứng nào, triệt tiêu nghẽn I/O đĩa và loại bỏ hoàn toàn nguy cơ bị ngắt kết nối do `SocketTimeoutException` (theo mốc 60 giây).

## 8. CAS / Lock-free Quota (`AtomicLong.compareAndSet`)
- **Quyết định của nhóm:** Quản lý hạn ngạch lưu trữ 10 GiB bằng giải thuật **CAS không khóa** (`AtomicLong`) thay vì bọc từ khóa `synchronized`.
- **Đặc trưng nổi bật:** Thao tác kiểm tra và dành chỗ dung lượng (`reserveSpace`) được xử lý bằng lệnh phần cứng vi xử lý trong vòng lặp `while (true) { compareAndSet(...) }`. Cực kỳ nhanh, hỗ trợ nhiều client cùng upload và **hoàn toàn không thể xảy ra Deadlock**.

## 9. Strict Lock Hierarchy (Thứ bậc lấy khóa một chiều)
- **Quyết định của nhóm:** Quy định thứ tự lấy khóa cố định một chiều:
  $$\text{SessionManager (Khóa ngoài)} \;\longrightarrow\; \text{TransferSession (Khóa giữa)} \;\longrightarrow\; \text{FileManager (Khóa trong)}$$
- **Đặc trưng nổi bật:** Tuyệt đối cấm gọi ngược chiều. Phá vỡ điều kiện chờ vòng tròn (*Circular Wait*), triệt tiêu hoàn toàn khả năng xảy ra Deadlock giữa các luồng Server khi vừa upload vừa xem danh sách file.

## 10. Snapshot before Processing (Tránh lồng khóa)
- **Quyết định của nhóm:** Trích xuất dữ liệu cần thiết ra danh sách tạm hoặc biến độc lập rồi **nhả khóa ngay lập tức** trước khi gọi sang phương thức của đối tượng khác.
- **Đặc trưng nổi bật:** Ví dụ khi dọn session quá hạn, luồng `ConnectionWatcher` chỉ khóa bảng session để lọc ra `List<TransferSession>` tạm rồi buông khóa ngay, sau đó mới duyệt xóa file trên đĩa. Không bao giờ giữ 2 khóa cùng lúc.

## 11. Checkpoint & Resume (Khôi phục khi đứt mạng)
- **Quyết định của nhóm:** Lưu vị trí byte đã ghi thực tế trên đĩa (`actualDiskSize`) và mã bí mật `resumeToken` 128-bit.
- **Đặc trưng nổi bật:** Khi mất mạng đột ngột (rút dây mạng, mất Wifi), Client kết nối lại gửi `UPLOAD_RESUME`, Server cho phép truyền tiếp ngay từ byte thực tế trên đĩa mà không phải truyền lại từ đầu, tiết kiệm thời gian và băng thông.

## 12. Idempotency & Giữ kết quả 24 giờ (Xử lý đứt mạng vạch đích)
- **Quyết định của nhóm:** Sau khi commit thành công, Server **giữ lại thông tin phiên hoàn tất trong RAM suốt 24 giờ** (`UPLOAD_STATE_COMPLETED`).
- **Đặc trưng nổi bật:** Giải quyết sự cố mất mạng đúng lúc Server vừa commit xong nhưng gói phản hồi chưa kịp tới Client. Khi có mạng lại, Client gửi `UPLOAD_RESUME` sẽ nhận lại ngay `completedFileId` trong 1 tích tắc mà **không phải upload lại dù chỉ 1 byte**.

## 13. Phân trang Pagination (Trần an toàn 128 KiB)
- **Quyết định của nhóm:** Chia nhỏ danh mục 10.000 file thành các trang 50 file (`OP_LIST_FILES`) thay vì gửi toàn bộ danh sách một lần.
- **Đặc trưng nổi bật:** Mỗi phản hồi chỉ tốn dưới 15 KiB, nằm gọn trong trần an toàn **128 KiB** của hệ thống. Ngăn chặn kẻ xấu gửi yêu cầu ảo làm cạn kiệt bộ nhớ Heap (`OutOfMemoryError`) của Server.

## 14. Chuỗi UTF-8 chuẩn (Khắc phục hạn chế của Java `writeUTF`)
- **Quyết định của nhóm:** Tự thiết kế phương thức `writeString()` ghi 4 byte độ dài `int` + mảng byte UTF-8 chuẩn thay vì dùng `DataOutputStream.writeUTF()`.
- **Đặc trưng nổi bật:** 
  1. Tránh chuẩn *Modified UTF-8* riêng của Java.
  2. Phá bỏ giới hạn độ dài 65.535 byte (64 KiB) do `writeUTF` dùng 2 byte unsigned short. Chuỗi tên file và thông báo hỗ trợ độ dài lớn an toàn.

## 15. Đổi tên file nguyên tử (Atomic Rename)
- **Quyết định của nhóm:** Ghi dữ liệu vào file tạm `.tmp`. Chỉ khi nhận lệnh `COMMIT` và kiểm tra mã SHA-256 trùng khớp 100% mới đổi tên thành file chính thức (`saveCompletedFile`).
- **Đặc trưng nổi bật:** Ngăn chặn tuyệt đối việc người dùng khác nhìn thấy hoặc tải về một file bị lỗi, file rác hoặc file chưa được nhận trọn vẹn.

## 16. Dọn dẹp lúc khởi động (Bootstrap Cleanup)
- **Quyết định của nhóm:** Tự động quét và xóa sạch toàn bộ các file `.tmp` mồ côi trong thư mục lưu trữ khi Server vừa khởi động.
- **Đặc trưng nổi bật:** Giải phóng dung lượng ổ cứng, tránh tích tụ rác đĩa do những lần mất điện hoặc tắt máy chủ đột ngột trước đó để lại.

## 17. Timeouts đa tầng (Bảo vệ tài nguyên)
- **Quyết định của nhóm:** Cài đặt các mốc thời gian chờ: Bắt tay 10s (`HANDSHAKE`), Nhận khung 15s (`FRAME` chống tấn công truyền từng byte chậm chạp Slowloris), Đọc dữ liệu 60s (`READ`).
- **Đặc trưng nổi bật:** Tự động phát hiện và ngắt các kết nối bị treo hoặc cố tình giam socket, giải phóng Worker Thread về Thread Pool cho các client khác sử dụng.
