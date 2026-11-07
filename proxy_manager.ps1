# Proxy Manager for Android (Pixel) via ADB
param(
    [Parameter(Position=0)]
    [ValidateSet("status", "enable", "disable", "connect", "install-cert")]
    [string]$Action = "status",

    [Parameter(Position=1)]
    [string]$Target = ""
)

$adb = "C:\Android\sdk\platform-tools\adb.exe"
$pcIp = "192.168.1.61"
$defaultProxyPort = "8080"

function Check-AdbDevice {
    $devices = & $adb devices | Select-String -Pattern "\b(device)\b"
    if (-not $devices) {
        Write-Warning "No active ADB device detected. Please connect via '.\proxy_manager.ps1 connect <IP:PORT>'"
        return $false
    }
    return $true
}

switch ($Action) {
    "connect" {
        if (-not $Target) {
            Write-Host "Usage: .\proxy_manager.ps1 connect <DEVICE_IP:PORT>" -ForegroundColor Yellow
            Write-Host "Check 'Settings > Developer options > Wireless debugging' on your Pixel for the IP & port."
            exit 1
        }
        Write-Host "Connecting to $Target..." -ForegroundColor Cyan
        & $adb connect $Target
        & $adb devices
    }

    "status" {
        Write-Host "--- ADB Connected Devices ---" -ForegroundColor Cyan
        & $adb devices
        
        Write-Host "`n--- Device Proxy Setting ---" -ForegroundColor Cyan
        $proxy = & $adb shell settings get global http_proxy
        if ($proxy -and $proxy -ne "null" -and $proxy -ne ":0") {
            Write-Host "Active Proxy: $proxy" -ForegroundColor Green
        } else {
            Write-Host "No proxy configured on device (direct connection)." -ForegroundColor Gray
        }
    }

    "enable" {
        if (-not (Check-AdbDevice)) { exit 1 }
        $port = if ($Target) { $Target } else { $defaultProxyPort }
        $proxyEndpoint = "$($pcIp):$port"

        Write-Host "Setting global HTTP proxy to $proxyEndpoint..." -ForegroundColor Cyan
        & $adb shell settings put global http_proxy $proxyEndpoint
        
        # Verify
        $current = & $adb shell settings get global http_proxy
        Write-Host "Proxy active on device: $current" -ForegroundColor Green
        Write-Host "Ensure your proxy tool (mitmweb / Charles / Fiddler) is listening on port $port!" -ForegroundColor Yellow
    }

    "disable" {
        if (-not (Check-AdbDevice)) { exit 1 }
        Write-Host "Disabling global proxy on device..." -ForegroundColor Cyan
        & $adb shell settings put global http_proxy :0
        & $adb shell settings delete global http_proxy
        & $adb shell settings delete global global_http_proxy_host
        & $adb shell settings delete global global_http_proxy_port
        Write-Host "Proxy cleared. Device is now on direct connection." -ForegroundColor Green
    }

    "install-cert" {
        if (-not (Check-AdbDevice)) { exit 1 }
        $certPath = "$env:USERPROFILE\.mitmproxy\mitmproxy-ca-cert.cer"
        if (Test-Path $certPath) {
            Write-Host "Pushing mitmproxy CA cert to /sdcard/Download/..." -ForegroundColor Cyan
            & $adb push $certPath /sdcard/Download/mitmproxy-ca-cert.cer
            Write-Host "Cert pushed to phone downloads." -ForegroundColor Green
            Write-Host "Opening Security Credentials settings on phone..." -ForegroundColor Cyan
            & $adb shell am start -a android.settings.SECURITY_SETTINGS
            Write-Host "On your Pixel, go to: More security settings > Encryption & credentials > Install a certificate > CA certificate, then choose mitmproxy-ca-cert.cer" -ForegroundColor Yellow
        } else {
            Write-Warning "Could not find $certPath. Start mitmproxy at least once to generate its CA certificate."
        }
    }
}
