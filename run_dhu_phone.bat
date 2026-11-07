@echo off
title Android Auto Desktop Head Unit (DHU) - Physical Phone
echo ========================================================
echo       Capture Engine - Android Auto (Physical Pixel 8)
echo ========================================================
echo.
echo Make sure "Start head unit server" is active in Android Auto Developer Settings!
echo.
echo [1/2] Forwarding port 5277 to Pixel 8...
"C:\Android\sdk\platform-tools\adb.exe" -s adb-4C181JEKB00822-t8szpm._adb-tls-connect._tcp forward tcp:5277 tcp:5277
echo.
echo [2/2] Launching Desktop Head Unit GUI...
"C:\Android\sdk\extras\google\auto\desktop-head-unit.exe" -c "C:\Android\sdk\extras\google\auto\config\default_720p.ini"
pause
