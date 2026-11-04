@echo off
chcp 65001 > nul
echo ===================================================
echo   LFTP/1 - KHỞI CHẠY MÁY CHỦ (SERVER)
echo ===================================================

cd /d "%~dp0"
if not exist "bin\lftp\server\ServerMain.class" (
    echo [!] Chưa tìm thấy mã biên dịch! Đang tự động biên dịch trước...
    call compile.bat
)

echo [*] Đang khởi động Server trên cổng TCP 5000 và UDP 5001...
java -cp bin lftp.server.ServerMain %*
pause

