---
name: pixel-wireless-sideload
description: >-
  Build and sideload the Android app wirelessly onto the user's Pixel 8 (or Android phone) over the local Wi-Fi network using ADB.
  Trigger ONLY when the user explicitly says something like "install on my pixel 8", "put it on my phone", "sideload to my phone",
  or "deploy to pixel". Never run or initiate deployment without explicit user instruction.
---

# Pixel 8 Wireless Sideload Skill

This skill documents how to build, wirelessly push, and launch the Android application onto the user's Pixel 8 (or Android device) over the local Wi-Fi network, as well as comprehensive troubleshooting steps when developer mode turns off or pairing breaks.

> [!IMPORTANT]
> **Execution Rule**: ONLY perform this deployment when explicitly instructed by the user (e.g., "install on my pixel 8", "put it on my phone", "deploy to my device"). Do not build or push to the physical device automatically during routine code edits.

---

## 1. Device & Environment Setup

- **Host Platform Tools / ADB**: `C:\Android\sdk\platform-tools\adb.exe`
- **Host Java JDK**: `C:\Android\jdk17`
- **Project Directory**: `c:\Users\Donegani\capture`
- **Output APK**: `app\build\outputs\apk\debug\app-debug.apk`
- **Package Name**: `com.capture.engine`
- **Main Activity**: `com.capture.engine.ui.MainActivity`
- **Helper Script**: `c:\Users\Donegani\capture\sideload_wireless.bat`
- **Browser Server Script**: `c:\Users\Donegani\capture\serve_apk.bat`

---

## 2. Standard Deployment Workflow

When the user gives the instruction to install on their phone:

### Step 1: Build the Debug APK
Ensure the latest code changes are compiled into the debug APK:
```powershell
$env:JAVA_HOME = "C:\Android\jdk17"
cd c:\Users\Donegani\capture
.\gradlew.bat assembleDebug
```

### Step 2: Check for Connected Device
Check if the device is already connected via ADB:
```powershell
& "C:\Android\sdk\platform-tools\adb.exe" devices
```

- **If device is listed as `device`** (e.g. `192.168.1.26:XXXXX  device`):
  Proceed directly to Step 4 (Install & Launch).

- **If no device is listed or listed as `offline`**:
  Proceed to Step 3 (Reconnect / Discover).

### Step 3: Discover & Connect
Android Wireless Debugging changes its connect port each time it is toggled or reconnected.
1. Check mDNS for the active connection service:
   ```powershell
   & "C:\Android\sdk\platform-tools\adb.exe" mdns services
   ```
2. If mDNS shows an entry like `_adb-tls-connect._tcp  192.168.1.X:PORT`, connect immediately:
   ```powershell
   & "C:\Android\sdk\platform-tools\adb.exe" connect 192.168.1.X:PORT
   ```
3. If not discovered, ask the user for the current **IP address & Port** displayed on their phone under **Settings > Developer options > Wireless debugging**.

### Step 4: Install & Launch
Once connected:
```powershell
& "C:\Android\sdk\platform-tools\adb.exe" -s <DEVICE_ID> install -r "c:\Users\Donegani\capture\app\build\outputs\apk\debug\app-debug.apk"
& "C:\Android\sdk\platform-tools\adb.exe" -s <DEVICE_ID> shell am start -n "com.capture.engine/com.capture.engine.ui.MainActivity"
```

---

## 3. Troubleshooting & Recovery Runbook

### Issue A: Developer Mode Gets Turned Off
Android updates or security toggles can occasionally turn Developer Options off.
1. On the Pixel 8, go to **Settings** → **About phone**.
2. Scroll to the bottom and locate **Build number**.
3. Tap **Build number** 7 times rapidly until a toast appears saying *"You are now a developer!"*.
4. Return to **Settings** → **System** → **Developer options**.

---

### Issue B: Wireless Debugging Disconnected / Port Changed
Android automatically toggles off Wireless Debugging when disconnecting from Wi-Fi or rebooting. When re-enabled, Android assigns a **new random connect port**.
1. On the Pixel 8, go to **Settings** → **System** → **Developer options**.
2. Make sure the phone is on the **same Wi-Fi network** as the computer.
3. Toggle **Wireless debugging** to **ON**.
4. Note the port shown under **IP address & Port** (e.g., `192.168.1.26:38475`).
5. Connect on PC:
   ```powershell
   & "C:\Android\sdk\platform-tools\adb.exe" connect 192.168.1.26:38475
   ```

---

### Issue C: Pairing Broken or "Connection Refused"
If the connection is rejected or unauthorized:
1. In **Settings** → **Developer options** → tap the text **Wireless debugging**.
2. Tap **Pair device with pairing code**.
   > [!WARNING]
   > Keep the popup dialog **open on screen**. If you close the dialog or let the screen sleep, the pairing port and code immediately expire.
3. The popup displays:
   - **Wi-Fi pairing code** (6 digits, e.g., `123456`)
   - **IP address & Port** (e.g., `192.168.1.26:44985`) — note this pairing port is different from the connection port.
4. Run on PC:
   ```powershell
   & "C:\Android\sdk\platform-tools\adb.exe" pair 192.168.1.26:44985 123456
   ```
5. Once "Successfully paired" appears:
   - Close the pairing dialog.
   - Look at the main **Wireless debugging** screen for the general connection `IP address & Port`.
   - Run `adb connect 192.168.1.26:<MAIN_PORT>`.

---

### Issue D: ADB Daemon Glitched or Stuck
If ADB is unresponsive or reports protocol faults:
```powershell
& "C:\Android\sdk\platform-tools\adb.exe" kill-server
& "C:\Android\sdk\platform-tools\adb.exe" start-server
```
Then retry `adb mdns services` or `adb connect`.

---

### Issue E: Instant Fallback via Browser (Zero ADB Required)
If wireless ADB cannot be established or is troublesome:
1. Start the local HTTP server on the PC:
   ```powershell
   python -m http.server 8000 --directory "c:\Users\Donegani\capture\app\build\outputs\apk\debug"
   ```
2. Find the PC Wi-Fi IP address (`192.168.1.61`).
3. On the Pixel 8, open Chrome and visit:
   ```
   http://192.168.1.61:8000
   ```
4. Tap **Download APK** and tap **Install** (allow "Install unknown apps" for Chrome if prompted).
