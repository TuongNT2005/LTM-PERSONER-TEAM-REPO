@echo off
chcp 65001 > nul
echo ===================================================
echo   LFTP/1 - BIÊN DỊCH TOÀN BỘ MÃ NGUỒN DỰ ÁN
echo ===================================================

cd /d "%~dp0"
if not exist "bin" mkdir "bin"

echo [*] Đang tìm kiếm các tệp mã nguồn Java (.java)...
powershell -NoProfile -Command "$files = Get-ChildItem -Path 'src' -Recurse -Filter '*.java' | ForEach-Object { '\"' + ($_.FullName -replace '\\', '/') + '\"' }; Set-Content -Path 'sources.txt' -Value $files -Encoding ascii"

if not exist "sources.txt" (
    echo [!] Không tìm thấy mã nguồn trong thư mục src!
    pause
    exit /b 1
)

echo [*] Đang tiến hành biên dịch với javac (Java 17+)...
javac -encoding UTF-8 -d bin @sources.txt

if %ERRORLEVEL% equ 0 (
    echo.
    echo [V] BIÊN DỊCH THÀNH CÔNG RỰC RỠ!
    echo [V] Toàn bộ file .class đã sẵn sàng trong thư mục bin/
    if exist "sources.txt" del "sources.txt"
) else (
    echo.
    echo [X] Biên dịch thất bại! Vui lòng kiểm tra thông báo lỗi ở trên.
)
pause

