# Project Proposal — Milestone M1

## 1. Team & Topic

- **Team:** *TKD* — *Nguyễn Trung Tường, Nguyễn Đình Khải, Bùi Văn Đạt*
- **Topic:** *Xây dựng ứng dụng truyền tệp trong mạng nội bộ sử dụng Java TCP Socket*

## 2. Problem

Xét tình huống A và B đang ngồi cạnh nhau trong văn phòng, cùng kết nối với mạng nội bộ của công ty và họ đang có nhu cầu truyền 1 tệp nội bộ dung lượng lớn cho nhau. Thông thường, nhu cầu này được giải quyết bằng cách A sẽ tải file này lên 1 kho lưu trữ chung vd là Drive và B sẽ đợi và tải file đó về máy thông qua drive. Cách làm này tuy giải quyết được nhu cầu trên nhưng nó lại làm nảy sinh một số vấn đề:

- Cách làm này phụ thuộc vào kết nối Internet do phải dùng dịch vụ của drive nên tốc độ truyền sẽ phụ thuộc vào tốc độ internet và khoảng cách truyền, tốc độ tải sẽ chậm nếu đường truyền yếu và khoảng cách truyền xa --> Tốc độ không ổn định

- Nếu làm theo cách trên thì tài liệu được truyền tới và lưu trữ ở bên thứ 3 là drive --> Không phù hợp với nhu cầu truyền các file nội bộ của công ty

## 3. Idea

Giải pháp của nhóm đưa ra là xây dựng 1 mô hình client-server chạy ngay trên mạng nội bộ, A có thể tải file lên server nội bộ, B cũng có thể tải file này về máy khi có nhu cầu. Khi đó:
- Dữ liệu được truyền và lưu trên các máy trong mạng nội bộ, không cần tải lên dịch vụ lưu trữ bên thứ ba, như vậy có thể phục vụ nhu cầu truyền các file nội bộ
- Truyền dữ liệu qua mạng nội bộ sẽ không phụ thuộc đường truyền Internet bên ngoài và tận dụng được hạ tầng mạng LAN hiện có

## 4. Scope

| Must have (M3) | Nice to have | Out of scope |
|----------------|--------------|--------------|
| Tải lên, xem danh sách file (hỗ trợ phân trang) và tải về file qua CLI khi LAN còn hoạt động, không cần Internet | Có giao diện đồ họa (GUI) | Không xây dựng cơ chế 2 client tự truyền file trực tiếp qua cho nhau (P2P) |
| Server phục vụ được nhiều client cùng lúc bằng Thread Pool có kiểm soát số luồng | Có tài khoản đăng nhập, lưu lịch sử hoạt động | Không truyền thư mục |
| Tự động tìm kiếm server trong mạng LAN bằng cơ chế UDP Broadcast mà không cần nhập IP thủ công | | Không hỗ trợ người dùng ngoài mạng nội bộ |
| Hiển thị tiến độ, tốc độ truyền (MB/s) và thời gian còn lại (ETA), cho phép người dùng tạm dừng hoặc hủy lượt truyền từ client | | Chưa mã hóa dữ liệu truyền trong mạng nội bộ giữa server và client |
| Có cơ chế kiểm tra tính toàn vẹn của file (tính SHA-256 lũy tiến) trước khi công nhận hoàn tất | | |
| Có cơ chế tiếp tục truyền file khi mất kết nối (resume upload/download) | | |
| Có cơ chế tự ngắt kết nối khi bên kia không có phản hồi trong 1 khoảng thời gian (timeout) | | |
| Đóng gói sản phẩm và môi trường bằng docker để tiện sử dụng | | |

## 5. Key Networking Challenge

- **Cắt gói và dính gói trong TCP:** Khi truyền dữ liệu dưới dạng luồng byte qua TCP, một thông điệp có thể phải đọc nhiều lần mới đủ hoặc nhiều thông điệp dính liền nhau. Nhóm thêm một trường 4 byte ghi độ dài trước mỗi thông điệp. Để tránh client gửi độ dài quá lớn làm cạn kiệt bộ nhớ của server, nhóm giới hạn độ dài thông điệp tối đa là 128 KiB và chia nhỏ danh sách file theo từng trang thay vì dồn tất cả vào một thông điệp.

- **Đồng bộ vị trí khi mất kết nối:** Bên gửi cần biết chính xác vị trí bên nhận đã lưu thành công vào đĩa để gửi tiếp đúng phần dữ liệu, tránh gửi trùng hoặc bỏ sót. Khi tải lên, server ghi đủ một phần dữ liệu rồi trả vị trí tiếp theo để client gửi tiếp. Khi tải xuống, client ghi đủ phần vừa nhận vào file tạm rồi yêu cầu phần tiếp theo. Nếu mất kết nối, client và server đối chiếu vị trí thực tế trên file tạm để truyền tiếp.

- **Quản lý đa luồng và tránh nghẽn, Deadlock:** Server phục vụ nhiều client cùng lúc nên cần quản lý luồng chặt chẽ. Nhóm dùng Thread Pool có giới hạn tối đa 20 luồng công việc để không làm cạn kiệt bộ nhớ stack của hệ điều hành. Các luồng dùng chung dữ liệu như danh sách file và hạn ngạch lưu trữ, vì vậy nhóm sắp xếp thứ tự lấy khóa rõ ràng và dùng các biến nguyên tử (AtomicLong) để tránh tình trạng các luồng chờ vòng tròn gây deadlock.

- **Tính toàn vẹn không làm nghẽn I/O:** Với file dung lượng lớn (lên tới 2 GiB), nếu đợi nhận xong toàn bộ mới đọc lại file từ đầu để tính SHA-256 thì sẽ làm nghẽn ổ đĩa và dễ bị quá hạn thời gian chờ trên socket (theo mốc 60 giây). Nhóm chọn giải pháp tính băm lũy tiến theo từng phần dữ liệu ngay khi vừa nhận được vào RAM, giúp bước xác nhận hoàn tất diễn ra tức thì mà không phải đọc lại đĩa cứng.

## 6. Initial Design Sketch

- **Transport:** 
  - TCP tới cổng 5000 của server để truyền dữ liệu file tin cậy và đúng thứ tự.
  - UDP Broadcast trên cổng 5001 để client tự tìm thấy địa chỉ IP của server trong mạng LAN.
- **Message framing & format:** Trường độ dài 4 byte (giới hạn tối đa 128 KiB), tiếp theo là phiên bản, mã lệnh, mã yêu cầu, trạng thái và nội dung. Số được ghi/đọc bằng DataOutputStream và DataInputStream, chuỗi dùng UTF-8 chuẩn, dữ liệu file được truyền nguyên dạng byte, không chuyển thành chuỗi. Mỗi kết nối gửi một yêu cầu rồi chờ phản hồi trước khi gửi tiếp.
- **First 3–5 commands:** 

    | Lệnh | Chiều gửi | Mục đích | 
    |------|-----------|----------|
    | HELLO | Client --> server | Bắt đầu kết nối và trao đổi các giới hạn |
    | LIST_FILES | Client --> server | Yêu cầu một trang danh sách file hoàn tất (kèm vị trí trang) |
    | UPLOAD_INIT | Client --> server | Khởi tạo lượt tải lên và kiểm tra chỗ trống |
    | UPLOAD_CHUNK | Client --> server | Gửi một phần dữ liệu kèm vị trí cần ghi |
    | DOWNLOAD_OPEN | Client --> server | Mở lượt tải xuống từ vị trí mong muốn |

- **Concurrency model:** 
  - Server: Sử dụng `ThreadPoolExecutor` với tối đa 20 worker threads và hàng đợi có giới hạn để nhận kết nối từ `ServerSocket.accept()`. Dùng `ConcurrentHashMap` và phân bậc thứ tự lấy khóa để bảo vệ trạng thái dùng chung.
  - Client: Sử dụng 2 luồng, luồng chính nhận lệnh bàn phím (cho phép hủy/dừng) và luồng phụ thực hiện truyền nhận dữ liệu mạng.
- **Language(s):** Java 21 (LTS), dùng ServerSocket, Socket, DatagramSocket, các luồng vào/ra có bộ đệm, dùng RandomAccessFile để đọc/ghi tại vị trí cần tiếp tục, MessageDigest để kiểm tra SHA-256 của file.

## 7. Alternatives Considered

| Lựa chọn cân nhắc | Nhóm chọn | Lý do lựa chọn |
|-------------------|:---------:|----------------|
| Dịch vụ trực tuyến / truyền trong LAN | LAN | Loại bỏ phụ thuộc vào internet khi trao đổi nội bộ, tận dụng hạ tầng mạng LAN hiện có |
| TCP / UDP | TCP cho dữ liệu, UDP cho tìm server | TCP đảm bảo thứ tự byte cho nội dung file, UDP broadcast giúp client tự tìm thấy server trong mạng nội bộ mà không cần gõ IP thủ công |
| Tạo luồng tự do / Dùng Thread Pool | Thread Pool | Tránh việc tạo quá nhiều luồng làm cạn kiệt bộ nhớ stack của hệ thống và gây lỗi OutOfMemoryError khi có nhiều kết nối |
| Danh sách file 1 lần / Phân trang | Phân trang (Pagination) | Giữ kích thước thông điệp nhỏ gọn (dưới 128 KiB), tránh việc server phải cấp phát bộ đệm quá lớn trên bộ nhớ Heap |
| Server trong LAN / P2P | Server trong LAN | Quản lý file và danh sách tập trung, thuận tiện chia sẻ nhiều người và demo dễ hơn |
| CLI / GUI | CLI trước | Tập trung thời gian hoàn thiện các cơ chế truyền nhận, xử lý lỗi mạng và đa luồng cốt lõi |

## 8. Ownership Plan

| Thành viên | Phân công phụ trách chính |
|------------|---------------------------|
| Nguyễn Trung Tường - B23DCCN909 | Viết chương trình Server, quản lý Thread Pool tiếp nhận kết nối (`ConnectionListener`, `ClientHandler`), xử lý lưu trữ file (`FileStorage`, `FileManager`), cơ chế quét dọn file tạm khi khởi động và quản lý thời gian chờ (`ConnectionWatcher`). |
| Bùi Văn Đạt - B23DCCN126 | Thiết kế giao thức nhị phân LFTP/1 và bộ đọc ghi thông điệp (`MessageIO`), quản lý thông tin các lượt truyền (`SessionManager`, `TransferSession`), điều phối luồng dữ liệu truyền tải (`UploadManager`, `DownloadManager`), tính toán mã băm SHA-256 lũy tiến và cơ chế tiếp tục truyền khi mất kết nối (`ResumeManager`). |
| Nguyễn Đình Khải - B23DCCN420 | Xây dựng ứng dụng Client đa luồng (`ClientMain`, `TransferClient`), luồng giao diện bàn phím (`ConsoleUI`) cho phép tạm dừng và hủy truyền, tính toán tốc độ truyền và ETA (`TransferProgress`), và phát triển tính năng tự tìm server qua UDP Broadcast. |

> Mỗi thành viên chịu trách nhiệm độc lập theo từng mảng chức năng từ giao thức, xử lý luồng mạng đến quản lý dữ liệu và giao diện, bảo đảm mọi thành viên đều hiểu sâu và tự bảo vệ được phần việc của mình.

## 9. Risks & Mitigation

| Rủi ro | Giải pháp khắc phục |
|--------|---------------------|
| Thông điệp bị cắt nhỏ hoặc dính liền nhau | Ghi độ dài 4 byte trước mỗi thông điệp, bên nhận đọc đủ số byte đó mới xử lý. Giới hạn độ dài tối đa 128 KiB để tránh tràn bộ đệm RAM. |
| Mất kết nối khi đang truyền dở | Giữ file tạm và vị trí đã ghi. Khi kết nối lại, hai bên đối chiếu vị trí thực tế trên đĩa để gửi tiếp đúng phần còn thiếu. |
| Tranh chấp dữ liệu và bế tắc (Deadlock) giữa các luồng | Sắp xếp thứ tự lấy khóa cố định từ SessionManager đến TransferSession rồi đến FileManager, dùng biến AtomicLong cho việc kiểm tra dung lượng để tránh lồng khóa. |
| Nhiều client kết nối làm cạn kiệt luồng và bộ nhớ | Dùng Thread Pool khống chế tối đa 20 luồng đồng thời, từ chối kết nối mới khi hàng đợi đã đầy. |
| Ổ đĩa server hết chỗ hoặc bị giữ chỗ quá lâu | Kiểm tra dung lượng trước khi nhận file, rút ngắn thời gian giữ lượt tải dở khi mất kết nối xuống 30 phút để giải phóng dung lượng cho người khác. |

## 10. Instructor Feedback

*(Sẽ điền sau khi bảo vệ M1.)*
