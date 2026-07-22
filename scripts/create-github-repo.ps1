# Requires: gh auth login
param(
    [string]$Owner = "starlit-moon",
    [string]$Repo = "starlitmoon-launcher",
    [switch]$Private
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Сначала выполните: gh auth login"
    exit 1
}

if (-not (git rev-parse HEAD 2>$null)) {
    git init -b main
    git add -A
    git commit -m "Initial commit: StarlitMoon Launcher"
}

$visibility = if ($Private) { "--private" } else { "--public" }
gh repo create "$Owner/$Repo" $visibility --source=. --remote=origin --description "Kotlin Desktop launcher for StarlitMoon Minecraft server" --push

Write-Host "Repository: https://github.com/$Owner/$Repo"
