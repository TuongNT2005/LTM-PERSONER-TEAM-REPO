@echo off
chcp 65001 > nul
echo ===================================================
echo   LFTP/1 - KHỞI CHẠY ỨNG DỤNG KHÁCH (CLIENT CLI)
echo ===================================================

cd /d "%~dp0"
if not exist "bin\lftp\client\ClientMain.class" (
    echo [!] Chưa tìm thấy mã biên dịch! Đang tự động biên dịch trước...
    call compile.bat
)

echo [*] Đang khởi động Client CLI...
java -cp bin lftp.client.ClientMain %*
pause

