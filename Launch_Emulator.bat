@echo off
title Android Emulator - Pixel 8
echo Starting Android Emulator (Pixel 8)...

set "ANDROID_HOME=C:\Android\sdk"
set "ANDROID_SDK_ROOT=C:\Android\sdk"
set "PATH=C:\Android\jdk17\bin;C:\Android\sdk\platform-tools;C:\Android\sdk\emulator;%PATH%"

cd /d "C:\Android\sdk\emulator"
start "" emulator.exe -avd Pixel_8

echo.
echo Waiting for device to be ready...
adb wait-for-device
echo Emulator connected! Waiting for system boot...
:wait_boot
adb shell getprop sys.boot_completed | find "1" >nul
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    goto wait_boot
)

echo.
echo Launching Capture Engine app...
adb shell am start -n "com.capture.engine/com.capture.engine.ui.MainActivity"
echo.
echo App launched successfully! You can keep this window open or close it.
pause
