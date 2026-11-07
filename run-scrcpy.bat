@echo off
title Scrcpy - Redmi Note 13 Pro 5G
cd /d "%~dp0scrcpy"
echo ========================================================
echo Dang ket noi man hinh Redmi Note 13 Pro 5G...
echo ========================================================
adb.exe devices
echo.
scrcpy.exe --no-audio --always-on-top --window-width=420
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [Loi] Scrcpy dung lai voi ma loi %ERRORLEVEL%.
    pause
)
