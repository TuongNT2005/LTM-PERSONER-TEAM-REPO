# Cấu trúc

1. int1433-05-20261-grp-int1433-05-0017
    Là folder của btl trong repo của thầy, chứa đủ code, doc cần nộp cho thầy

2. team's docs
    Là foler chứa các file md gồm file docs trong foler `int1433-05-20261-grp-int1433-05-0017` và các file hướng dẫn học

# Nhiệm vụ

1. Mỗi người vào team's docs đọc file hướng dẫn của mình và học theo. Đọc các file `proposal.md`, `architectureV2.md` và `protocal.md`

2. Clone github nhóm của thầy(ko phải repo này) về máy

1. Mỗi lần commit xem nội dung trong `commit-roadmap.md` để biết cần commit những file nào, message là gì, ghi `ai-log.md` ra sao

1. Khi biết các file cần commit và nội dung cần ghi vào `ai-log.md` là gì rồi thì tìm các file tương ứng trong folder được clone từ repo này rồi coppy vào vị trí tương ứng trong folder clone từ repo của thầy. Sau đó ghi lại `ai-log.md` với nội dung trong `commit-roadmap.md`

1. Làm xong rồi thì mở gitbash tại folder clone từ repo của thầy(chú ý check đường dẫn)
    ```bash
    git checkout -b <tên_nhánh(tuong, dat, khai)> // Nếu chưa có nhánh riêng thì tạo và chuyển sang nhánh mới

    git checkout <tên nhánh> // nếu đã có nhánh riêng rồi thì sử dụng lệnh này để chuyển sang nhánh đó

    git add fileA fileB ...
    git commit -m "message từ commit-roadmap.md"
    git push origin <tên nhánh>

    // Vào github --> Repo của nhóm(có đuôi là 0017) --> Nhấn nút tạo Pull request --> Điền message, tích vào cái phiếu(tích 3 cái cuối, 2 cái ở trên thì tích cái sử dụng ai) --> Xác nhận
    ```


4. Xong thì @all báo với nhóm trong zalo để người tiếp theo push

**CHÚ Ý: PHẢI HỌC CÁC NỘI DUNG TRONG FILE HƯỚNG DẪN THẬT NGHIÊM TÚC VÀ PHẢI HIỂU CHO TÔI!!**