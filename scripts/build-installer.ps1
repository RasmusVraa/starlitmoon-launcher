#Requires -Version 5
param([string]$Version = "1.1.3")
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$gradle = Join-Path $Root ".gradle-dist\gradle-8.12.1\bin\gradle.bat"
if (Test-Path $gradle) {
  & $gradle createReleaseDistributable --no-daemon
} else {
  & .\gradlew.bat createReleaseDistributable --no-daemon
}

$iscc = Join-Path $env:LOCALAPPDATA "Programs\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) {
  $iscc = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
}
if (-not (Test-Path $iscc)) { throw "ISCC.exe not found" }

New-Item -ItemType Directory -Force -Path "dist\v$Version" | Out-Null
& $iscc "installer\starlitmoon.iss"
Write-Host "OK: dist\v$Version\StarlitMoonLauncher-Setup-$Version.exe"
