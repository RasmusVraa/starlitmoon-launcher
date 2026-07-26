#Requires -Version 5
param(
  [string]$Version = "1.8.6",
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

# Compress-Archive on Windows stores entry names with `\`, which breaks the
# 1.8.3+ self-updater ZIP check (expects app/StarlitMoonLauncher.cfg).
function New-ForwardSlashZip([string]$SourceDir, [string]$ZipPath) {
  Add-Type -AssemblyName System.IO.Compression
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $absZip = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ZipPath)
  $zipDir = Split-Path -Parent $absZip
  if (-not (Test-Path $zipDir)) { New-Item -ItemType Directory -Force -Path $zipDir | Out-Null }
  if (Test-Path $absZip) { Remove-Item $absZip -Force }
  $zip = [System.IO.Compression.ZipFile]::Open($absZip, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    $root = (Resolve-Path $SourceDir).Path.TrimEnd('\', '/')
    Get-ChildItem -LiteralPath $root -Recurse -File | ForEach-Object {
      $rel = $_.FullName.Substring($root.Length + 1).Replace('\', '/')
      [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip,
        $_.FullName,
        $rel,
        [System.IO.Compression.CompressionLevel]::Optimal
      )
    }
  } finally {
    $zip.Dispose()
  }
}

function Assert-ZipHasForwardSlashCfg([string]$ZipPath, [string]$ExpectedVersion) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $z = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $ZipPath).Path)
  try {
    $cfg = $z.GetEntry("app/StarlitMoonLauncher.cfg")
    if ($null -eq $cfg) {
      $names = ($z.Entries | ForEach-Object { $_.FullName } | Where-Object { $_ -match 'StarlitMoonLauncher\.cfg' }) -join ', '
      throw "ZIP missing forward-slash entry app/StarlitMoonLauncher.cfg (found: $names). Refusing to ship."
    }
    $sr = New-Object System.IO.StreamReader($cfg.Open())
    try { $text = $sr.ReadToEnd() } finally { $sr.Close() }
    if ($text -notmatch [regex]::Escape("jpackage.app-version=$ExpectedVersion")) {
      throw "ZIP cfg version mismatch: expected jpackage.app-version=$ExpectedVersion"
    }
    Write-Host "OK: ZIP entry app/StarlitMoonLauncher.cfg has jpackage.app-version=$ExpectedVersion"
  } finally {
    $z.Dispose()
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
New-ForwardSlashZip -SourceDir $appDir -ZipPath $zipPath
Assert-ZipHasForwardSlashCfg -ZipPath $zipPath -ExpectedVersion $Version
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
