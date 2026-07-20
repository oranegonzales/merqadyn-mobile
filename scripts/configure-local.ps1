[CmdletBinding()]
param(
    [ValidateSet("Emulator", "UsbPhone", "LanPhone")]
    [string]$Target = "UsbPhone",
    [string]$ApiEnvPath = (Join-Path $PSScriptRoot "..\..\merqadyn-api\.env"),
    [string]$PhoneApiUrl,
    [string]$DeviceId = "55555555-5555-4555-8555-555555555551",
    [string]$MerchantId = "11111111-1111-4111-8111-111111111111",
    [string]$AdminApiUrl = "http://127.0.0.1:8080"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-EnvironmentFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "API environment file was not found at $Path. Start merqadyn-apido with scripts/start-local.ps1 first."
    }
    $result = @{}
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $result[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
        }
    }
    return $result
}

function Get-LanAddress {
    $route = Get-NetRoute -DestinationPrefix "0.0.0.0/0" -ErrorAction Stop |
        Sort-Object RouteMetric, InterfaceMetric |
        Select-Object -First 1
    if (-not $route) { throw "No active IPv4 route was found. Supply -PhoneApiUrl explicitly." }
    $address = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $route.InterfaceIndex -ErrorAction Stop |
        Where-Object { $_.IPAddress -notlike "169.254.*" } |
        Select-Object -First 1 -ExpandProperty IPAddress
    if (-not $address) { throw "No private LAN address was found. Supply -PhoneApiUrl explicitly." }
    return $address
}

$apiEnv = [System.IO.Path]::GetFullPath($ApiEnvPath)
$localProperties = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\local.properties"))
$values = Read-EnvironmentFile -Path $apiEnv
$userKey = @("MERQADYN_ADMIN_USER", "ADMIN_USER", "SPRING_SECURITY_USER_NAME") |
    Where-Object { $values.ContainsKey($_) } |
    Select-Object -First 1
$passwordKey = @("MERQADYN_ADMIN_PASSWORD", "ADMIN_PASSWORD", "SPRING_SECURITY_USER_PASSWORD") |
    Where-Object { $values.ContainsKey($_) } |
    Select-Object -First 1
if (-not $userKey -or -not $passwordKey) {
    throw "Could not find the Merqadyn admin username and password in $apiEnv"
}

switch ($Target) {
    "Emulator" { $resolvedPhoneUrl = "http://10.0.2.2:8080/" }
    "UsbPhone" {
        $adb = Get-Command adb -ErrorAction SilentlyContinue
        if (-not $adb) { throw "adb is unavailable. Install Android platform-tools or use -Target LanPhone." }
        & adb reverse tcp:8080 tcp:8080
        if ($LASTEXITCODE -ne 0) { throw "adb reverse failed. Connect the unlocked phone with USB debugging enabled and approve this computer." }
        $resolvedPhoneUrl = "http://127.0.0.1:8080/"
    }
    "LanPhone" {
        if ($PhoneApiUrl) {
            $resolvedPhoneUrl = $PhoneApiUrl
        } else {
            $resolvedPhoneUrl = "http://$(Get-LanAddress):8080/"
        }
    }
}

if (-not $resolvedPhoneUrl.EndsWith("/")) { $resolvedPhoneUrl += "/" }

$pair = "$($values[$userKey]):$($values[$passwordKey])"
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
$enrollmentUrl = "$($AdminApiUrl.TrimEnd('/'))/api/v1/merchants/$MerchantId/devices/$DeviceId/enrollment"
try {
    $enrollment = Invoke-RestMethod -Method Post -Uri $enrollmentUrl -Headers @{ Authorization = "Basic $basic" } -TimeoutSec 15
} catch {
    throw "Could not create an enrollment code at $enrollmentUrl. Confirm the API is healthy, then try again. $($_.Exception.Message)"
}

$preserved = @()
if (Test-Path $localProperties) {
    $preserved = Get-Content $localProperties | Where-Object { -not $_.StartsWith("MERQADYN_") }
}
$settings = @(
    "MERQADYN_API_URL=$resolvedPhoneUrl"
    "MERQADYN_DEVICE_ID=$DeviceId"
)
$encoding = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($localProperties, @($preserved + $settings), $encoding)

Write-Host ""
Write-Host "Merqadyn phone enrollment"
Write-Host "API address: $resolvedPhoneUrl"
Write-Host "Device ID:   $DeviceId"
Write-Host "Code:        $($enrollment.code)"
Write-Host "Expires:     $($enrollment.expiresAt)"
Write-Host ""
Write-Host "Build and install the debug app, then enter this one-time code on the enrollment screen."
if ($Target -eq "LanPhone") {
    Write-Host "Keep the phone and computer on the same trusted network and allow inbound TCP 8080 only on the Windows Private firewall profile."
}
Write-Host "No administrator password was written to the Android project."
