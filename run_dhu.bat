@echo off
title Android Auto Desktop Head Unit (DHU)
echo ========================================================
echo       Capture Engine - Android Auto Desktop Test
echo ========================================================
echo.
echo Detected devices:
"C:\Android\sdk\platform-tools\adb.exe" devices
echo.

:: Try emulator first, fallback to first available device
set TARGET_DEVICE=emulator-5554
echo [1/2] Forwarding port 5277 via ADB (-s %TARGET_DEVICE%)...
"C:\Android\sdk\platform-tools\adb.exe" -s %TARGET_DEVICE% forward tcp:5277 tcp:5277
if %ERRORLEVEL% NEQ 0 (
    echo Forwarding on default device...
    "C:\Android\sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
)

echo.
echo [2/2] Starting Desktop Head Unit GUI...
"C:\Android\sdk\extras\google\auto\desktop-head-unit.exe" -c "C:\Android\sdk\extras\google\auto\config\default_720p.ini"
pause
