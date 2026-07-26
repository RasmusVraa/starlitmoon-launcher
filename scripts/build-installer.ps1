#Requires -Version 5
param(
  [string]$Version = "1.8.4",
  [switch]$Sign
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

function Assert-ExitCode([string]$Step) {
  if ($null -eq $LASTEXITCODE) { return }
  if ($LASTEXITCODE -ne 0) {
    throw "$Step failed with exit code $LASTEXITCODE"
  }
}

$gradle = Join-Path $Root ".gradle-dist\gradle-8.12.1\bin\gradle.bat"
if (Test-Path $gradle) {
  & $gradle createReleaseDistributable --no-daemon
} else {
  & .\gradlew.bat createReleaseDistributable --no-daemon
}
Assert-ExitCode "Gradle createReleaseDistributable"

$appDir = "build\compose\binaries\main-release\app\StarlitMoonLauncher"
if (-not (Test-Path $appDir)) { throw "App dir missing: $appDir" }

$cfg = Join-Path $appDir "app\StarlitMoonLauncher.cfg"
if (-not (Test-Path $cfg)) { throw "Missing cfg: $cfg" }
$cfgText = Get-Content $cfg -Raw
if ($cfgText -notmatch [regex]::Escape("jpackage.app-version=$Version")) {
  throw "Packaged app version mismatch: expected jpackage.app-version=$Version in $cfg (stale build?). Refusing to ship mislabeled release."
}
$jar = Get-ChildItem (Join-Path $appDir "app\starlitmoon-launcher-$Version-*.jar") -ErrorAction SilentlyContinue |
  Select-Object -First 1
if (-not $jar) {
  throw "Missing main jar starlitmoon-launcher-$Version-*.jar under app\ (stale build?)"
}
Write-Host "OK: packaged app is $Version ($($jar.Name))"

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
Assert-ExitCode "Inno Setup ISCC"
Write-Host "OK: dist\v$Version\StarlitMoonLauncher-Setup-$Version.exe"

$pfx = Join-Path $Root "certs\starlitmoon-codesign.pfx"
if ($Sign -or (Test-Path $pfx)) {
  & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root "scripts\sign-release.ps1") -Version $Version
  Assert-ExitCode "sign-release"
}
