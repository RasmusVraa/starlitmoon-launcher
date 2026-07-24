#Requires -Version 5
<#
.SYNOPSIS
  Подписывает Setup/portable EXE self-signed сертификатом (бесплатно).

  Использует Set-AuthenticodeSignature (Windows SDK / signtool не нужны).
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$PfxPath = "",
    [string]$PasswordFile = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not $PfxPath) { $PfxPath = Join-Path $Root "certs\starlitmoon-codesign.pfx" }
if (-not $PasswordFile) { $PasswordFile = Join-Path $Root "certs\pfx-password.txt" }

if (-not (Test-Path $PfxPath)) {
    throw "No cert. Run: .\scripts\create-dev-codesign-cert.ps1"
}
if (-not (Test-Path $PasswordFile)) {
    throw "Missing password file: $PasswordFile"
}

$plain = (Get-Content $PasswordFile -Raw).Trim()
$secure = ConvertTo-SecureString -String $plain -Force -AsPlainText
$cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(
    $PfxPath,
    $secure,
    [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::Exportable
)

$candidates = @(
    Join-Path $Root "dist\v$Version\StarlitMoonLauncher-Setup-$Version.exe"
    Join-Path $Root "build\compose\binaries\main-release\exe\StarlitMoonLauncher-$Version.exe"
)

# Also accept any extra paths.
$files = @()
foreach ($p in $candidates) {
    if (Test-Path $p) { $files += (Resolve-Path $p).Path }
}
if ($files.Count -eq 0) {
    throw "No EXE found for v$Version. Build installer/portable first."
}

$ts = "http://timestamp.digicert.com"
foreach ($file in $files) {
    Write-Host "Signing $file ..."
    $result = Set-AuthenticodeSignature -FilePath $file -Certificate $cert -TimestampServer $ts -HashAlgorithm SHA256
    if ($result.Status -ne "Valid" -and $result.Status -ne "UnknownError") {
        # UnknownError sometimes appears with self-signed + timestamp; verify StatusMessage.
        Write-Warning "Status=$($result.Status) $($result.StatusMessage)"
    }
    $check = Get-AuthenticodeSignature -FilePath $file
    Write-Host "  -> $($check.Status) publisher=$($check.SignerCertificate.Subject)"
}

Write-Host "Done."
Write-Host "NOTE: Smart App Control on other PCs still blocks self-signed. Use SignPath for public trust."
