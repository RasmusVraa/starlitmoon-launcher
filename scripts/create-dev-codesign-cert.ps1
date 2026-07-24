#Requires -Version 5
<#
.SYNOPSIS
  Создаёт бесплатный self-signed Code Signing сертификат StarlitMoon.

.NOTES
  Self-signed НЕ проходит Smart App Control / SmartScreen у чужих ПК
  (нужен CA вроде SignPath Foundation — см. scripts/signpath-apply.txt).

  Для ТЕБЯ локально: после создания поставь .cer в Trusted Root + Trusted Publishers
  (скрипт предлагает), тогда «неизвестный издатель» на этой машине уйдёт.
#>
param(
    [string]$Subject = "CN=StarlitMoon Launcher, O=StarlitMoon, C=RU",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not $OutDir) { $OutDir = Join-Path $Root "certs" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$pfxPath = Join-Path $OutDir "starlitmoon-codesign.pfx"
$cerPath = Join-Path $OutDir "starlitmoon-codesign.cer"
$pwdFile = Join-Path $OutDir "pfx-password.txt"

if (Test-Path $pfxPath) {
    Write-Host "Already exists: $pfxPath"
    Write-Host "Delete it first to recreate."
    exit 0
}

# Random password kept next to pfx (local only — certs/ is gitignored).
$plain = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
$secure = ConvertTo-SecureString -String $plain -Force -AsPlainText
Set-Content -Path $pwdFile -Value $plain -Encoding ASCII

$cert = New-SelfSignedCertificate `
    -Type CodeSigningCert `
    -Subject $Subject `
    -KeyAlgorithm RSA `
    -KeyLength 4096 `
    -HashAlgorithm SHA256 `
    -CertStoreLocation "Cert:\CurrentUser\My" `
    -KeyExportPolicy Exportable `
    -NotAfter (Get-Date).AddYears(5) `
    -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3")

Export-PfxCertificate -Cert $cert -FilePath $pfxPath -Password $secure | Out-Null
Export-Certificate -Cert $cert -FilePath $cerPath -Type CERT | Out-Null

# Local trust so THIS machine treats the publisher as known.
$pubStore = "Cert:\CurrentUser\TrustedPublisher"
$rootStore = "Cert:\CurrentUser\Root"
$existingPub = Get-ChildItem $pubStore -ErrorAction SilentlyContinue | Where-Object { $_.Thumbprint -eq $cert.Thumbprint }
if (-not $existingPub) {
    $cerObj = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($cerPath)
    $store = New-Object System.Security.Cryptography.X509Certificates.X509Store("TrustedPublisher", "CurrentUser")
    $store.Open("ReadWrite")
    $store.Add($cerObj)
    $store.Close()
}
$existingRoot = Get-ChildItem $rootStore -ErrorAction SilentlyContinue | Where-Object { $_.Thumbprint -eq $cert.Thumbprint }
if (-not $existingRoot) {
    Write-Host "Installing into CurrentUser\Root (self-signed CA trust for this user)..."
    $cerObj = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($cerPath)
    $store = New-Object System.Security.Cryptography.X509Certificates.X509Store("Root", "CurrentUser")
    $store.Open("ReadWrite")
    # May show a Windows trust prompt once.
    $store.Add($cerObj)
    $store.Close()
}

Write-Host ""
Write-Host "Created:"
Write-Host "  PFX:  $pfxPath"
Write-Host "  CER:  $cerPath"
Write-Host "  Pass: $pwdFile"
Write-Host "  Thumbprint: $($cert.Thumbprint)"
Write-Host ""
Write-Host "Next: .\scripts\sign-release.ps1 -Version 1.3.1"
Write-Host "Public SAC fix: apply at https://signpath.org/ (see scripts/signpath-apply.txt)"
