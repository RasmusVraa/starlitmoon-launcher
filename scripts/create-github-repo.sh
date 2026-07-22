#!/usr/bin/env bash
# Requires: gh auth login
set -euo pipefail

OWNER="${1:-starlit-moon}"
REPO="${2:-starlitmoon-launcher}"
VISIBILITY="${3:-public}" # public | private

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v gh >/dev/null 2>&1; then
  echo "Установите GitHub CLI: https://cli.github.com/"
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Сначала выполните: gh auth login"
  exit 1
fi

if ! git rev-parse HEAD >/dev/null 2>&1; then
  git init -b main
  git add -A
  git commit -m "Initial commit: StarlitMoon Launcher"
fi

# Ensure branch is main
git branch -M main 2>/dev/null || true

if git remote get-url origin >/dev/null 2>&1; then
  echo "Remote origin уже есть: $(git remote get-url origin)"
else
  gh repo create "${OWNER}/${REPO}" \
    --"${VISIBILITY}" \
    --source=. \
    --remote=origin \
    --description "Kotlin Desktop launcher for StarlitMoon Minecraft server" \
    --push
fi

echo "Repository: https://github.com/${OWNER}/${REPO}"
