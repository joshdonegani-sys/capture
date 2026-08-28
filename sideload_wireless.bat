@echo off
title Sideload to Android Device (Wireless)
echo ========================================================
echo        Capture Engine - Wireless Sideload Tool
echo ========================================================
echo.

set "ANDROID_HOME=C:\Android\sdk"
set "ANDROID_SDK_ROOT=C:\Android\sdk"
set "PATH=C:\Android\jdk17\bin;C:\Android\sdk\platform-tools;%PATH%"

set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"

if not exist "%APK_PATH%" (
    echo [ERROR] APK not found at: %APK_PATH%
    echo Please build the project first.
    pause
    exit /b 1
)

:menu
echo Choose an option:
echo   [1] Install to already connected device
echo   [2] Connect to device (IP:Port already paired)
echo   [3] Pair a new device with pairing code (Android 11+)
echo   [4] Exit
echo.
set /p CHOICE="Select [1-4]: "

if "%CHOICE%"=="1" goto install
if "%CHOICE%"=="2" goto connect
if "%CHOICE%"=="3" goto pair
if "%CHOICE%"=="4" exit /b 0
goto menu

:pair
echo.
echo On your Android device:
echo   1. Settings ^> Developer options ^> Wireless debugging (Enable)
echo   2. Tap 'Pair device with pairing code'
echo.
set /p PAIR_ADDR="Enter IP:PORT shown in pairing popup (e.g. 192.168.1.50:37123): "
set /p PAIR_CODE="Enter 6-digit pairing code: "
echo Pairing...
adb pair %PAIR_ADDR% %PAIR_CODE%
echo.
set /p CONNECT_ADDR="Enter the main IP:PORT shown on Wireless debugging screen: "
adb connect %CONNECT_ADDR%
goto install

:connect
echo.
set /p CONNECT_ADDR="Enter device IP:PORT (e.g. 192.168.1.50:5555): "
adb connect %CONNECT_ADDR%
goto install

:install
echo.
echo Checking connected devices...
adb devices
echo.
echo Installing app-debug.apk ...
adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo.
    echo [FAIL] Installation failed. Ensure device is authorized and screen is unlocked.
) else (
    echo.
    echo [SUCCESS] App installed successfully!
    echo Launching app...
    adb shell am start -n "com.capture.engine/com.capture.engine.ui.MainActivity"
)
pause
