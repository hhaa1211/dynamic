@echo off
title Redmi Note 13 Pro 5G - Scrcpy
cd /d "%~dp0scrcpy"
echo Dang ket noi man hinh Redmi Note 13 Pro 5G...
scrcpy.exe --no-audio --always-on-top --window-width=400 --stay-awake
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [Loi] Khong the mo man hinh. Chi tiet xem o tren.
    pause
)
