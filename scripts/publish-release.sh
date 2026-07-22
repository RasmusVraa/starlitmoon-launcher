#!/usr/bin/env bash
# Requires: gh auth login, JDK 17+
set -euo pipefail

VERSION="${1:-1.0.0}"
OWNER="${2:-RasmusVraa}"
REPO="${3:-starlitmoon-launcher}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Building StarlitMoon Launcher v${VERSION}..."
if [[ -f ./gradlew ]]; then
  ./gradlew packageReleaseDistribution --no-daemon
elif [[ -f ./gradlew.bat ]]; then
  ./gradlew.bat packageReleaseDistribution --no-daemon
elif [[ -x "$ROOT/.gradle-dist/gradle-8.12.1/bin/gradle" ]]; then
  "$ROOT/.gradle-dist/gradle-8.12.1/bin/gradle" packageReleaseDistribution --no-daemon
else
  echo "Gradle не найден"
  exit 1
fi

EXE="$(ls "build/compose/binaries/main-release/exe/StarlitMoonLauncher-${VERSION}.exe" 2>/dev/null || true)"
if [[ -z "$EXE" ]]; then
  EXE="$(ls build/compose/binaries/main-release/exe/*.exe 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$EXE" || ! -f "$EXE" ]]; then
  echo "EXE не найден после сборки"
  exit 1
fi

echo "Built: $EXE"

if ! gh auth status >/dev/null 2>&1; then
  echo "Run: gh auth login"
  exit 1
fi

TAG="v${VERSION}"
git add -A
if ! git diff --cached --quiet; then
  git commit -m "Release ${TAG}"
fi
git push origin main
git tag -a "$TAG" -m "Release $TAG" -f
git push origin "$TAG" -f

gh release create "$TAG" "$EXE" \
  --repo "${OWNER}/${REPO}" \
  --title "StarlitMoon Launcher ${TAG}" \
  --notes "Windows installer for StarlitMoon Minecraft launcher." \
  --clobber 2>/dev/null || \
gh release create "$TAG" "$EXE" \
  --repo "${OWNER}/${REPO}" \
  --title "StarlitMoon Launcher ${TAG}" \
  --notes "Windows installer for StarlitMoon Minecraft launcher."

echo "Release: https://github.com/${OWNER}/${REPO}/releases/tag/${TAG}"
