@echo off
title APK Web Server
echo ========================================================
echo        Capture Engine - Over-the-Air APK Server
echo ========================================================
echo.
echo Server running at:
echo   http://192.168.1.61:8000
echo.
echo Open this address in Chrome or any browser on your Android device.
echo Press Ctrl+C to stop the server when done.
echo ========================================================
echo.

python -m http.server 8000 --directory "%~dp0app\build\outputs\apk\debug"
