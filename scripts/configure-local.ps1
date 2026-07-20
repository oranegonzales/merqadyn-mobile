param(
    [string]$ApiEnvPath = (Join-Path $PSScriptRoot "..\..\merqadyn-api\.env")
)

$ErrorActionPreference = "Stop"

$apiEnv = [System.IO.Path]::GetFullPath($ApiEnvPath)
$localProperties = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\local.properties"))

if (-not (Test-Path $apiEnv)) {
    throw "API environment file was not found at $apiEnv"
}

$values = @{}
Get-Content $apiEnv | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
}

$userKey = @("MERQADYN_ADMIN_USER", "ADMIN_USER", "SPRING_SECURITY_USER_NAME") | Where-Object { $values.ContainsKey($_) } | Select-Object -First 1
$passwordKey = @("MERQADYN_ADMIN_PASSWORD", "ADMIN_PASSWORD", "SPRING_SECURITY_USER_PASSWORD") | Where-Object { $values.ContainsKey($_) } | Select-Object -First 1

if (-not $userKey -or -not $passwordKey) {
    throw "Could not find the Merqadyn admin username and password in $apiEnv"
}

$preserved = @()
if (Test-Path $localProperties) {
    $preserved = Get-Content $localProperties | Where-Object { -not $_.StartsWith("MERQADYN_") }
}

$settings = @(
    "MERQADYN_API_URL=http://10.0.2.2:8080/"
    "MERQADYN_ADMIN_USER=$($values[$userKey])"
    "MERQADYN_ADMIN_PASSWORD=$($values[$passwordKey])"
    "MERQADYN_DEVICE_ID=55555555-5555-4555-8555-555555555551"
)

@($preserved + $settings) | Set-Content -Path $localProperties -Encoding UTF8

Write-Host "Configured Merqadyn Mobile for the Android emulator."
Write-Host "Credentials were written only to ignored local.properties."
