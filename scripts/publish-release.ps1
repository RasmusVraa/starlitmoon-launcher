# Requires: gh auth login, JDK 17+
param(
    [string]$Version = "1.0.0",
    [string]$Owner = "RasmusVraa",
    [string]$Repo = "starlitmoon-launcher"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "Building StarlitMoon Launcher v$Version..."
if (Test-Path ".\gradlew.bat") {
    .\gradlew.bat packageReleaseDistribution --no-daemon
} else {
    & "$Root\.gradle-dist\gradle-8.12.1\bin\gradle.bat" packageReleaseDistribution --no-daemon
}

$exe = Get-ChildItem "build\compose\binaries\main-release\exe\StarlitMoonLauncher-$Version.exe" -ErrorAction SilentlyContinue
if (-not $exe) {
    $exe = Get-ChildItem "build\compose\binaries\main-release\exe\*.exe" | Select-Object -First 1
}
if (-not $exe) { throw "EXE not found after build" }

Write-Host "Built: $($exe.FullName) ($([math]::Round($exe.Length/1MB, 1)) MB)"

gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Run: gh auth login"
    exit 1
}

$tag = "v$Version"
git tag -a $tag -m "Release $tag" -f
git push origin main --tags

gh release create $tag $exe.FullName `
    --repo "$Owner/$Repo" `
    --title "StarlitMoon Launcher $tag" `
    --notes "Windows installer for StarlitMoon Minecraft launcher."

Write-Host "Release published: https://github.com/$Owner/$Repo/releases/tag/$tag"
