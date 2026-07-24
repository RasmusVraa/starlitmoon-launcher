#Requires -Version 5
param(
  [string]$Version = "1.3.3",
  [switch]$Sign
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$gradle = Join-Path $Root ".gradle-dist\gradle-8.12.1\bin\gradle.bat"
if (Test-Path $gradle) {
  & $gradle createReleaseDistributable --no-daemon
} else {
  & .\gradlew.bat createReleaseDistributable --no-daemon
}

$appDir = "build\compose\binaries\main-release\app\StarlitMoonLauncher"
if (-not (Test-Path $appDir)) { throw "App dir missing: $appDir" }

New-Item -ItemType Directory -Force -Path "dist\v$Version" | Out-Null
$zipPath = "dist\v$Version\StarlitMoonLauncher-$Version-windows.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
# Compress contents of app folder to ZIP root (needed for in-launcher updates).
Compress-Archive -Path (Join-Path $appDir "*") -DestinationPath $zipPath -CompressionLevel Optimal
Write-Host "OK: $zipPath"

$iscc = Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) {
  $iscc = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
}
if (-not (Test-Path $iscc)) { throw "ISCC.exe not found" }

& $iscc "installer\starlitmoon.iss"
Write-Host "OK: dist\v$Version\StarlitMoonLauncher-Setup-$Version.exe"

$pfx = Join-Path $Root "certs\starlitmoon-codesign.pfx"
if ($Sign -or (Test-Path $pfx)) {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root "scripts\sign-release.ps1") -Version $Version
}
