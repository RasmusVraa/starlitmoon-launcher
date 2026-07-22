#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
VERSION="${1:-1.0.3}"

GRADLE="$ROOT/.gradle-dist/gradle-8.12.1/bin/gradle"
if [[ -x "$GRADLE" ]]; then
  "$GRADLE" createReleaseDistributable --no-daemon
elif [[ -f ./gradlew.bat ]]; then
  ./gradlew.bat createReleaseDistributable --no-daemon
else
  echo "Gradle not found"
  exit 1
fi

ISCC="${ISCC:-$LOCALAPPDATA/Programs/Inno Setup 6/ISCC.exe}"
if [[ ! -f "$ISCC" ]]; then
  ISCC="/c/Program Files (x86)/Inno Setup 6/ISCC.exe"
fi
if [[ ! -f "$ISCC" ]]; then
  echo "Inno Setup ISCC.exe not found"
  exit 1
fi

mkdir -p "dist/v${VERSION}"
"$ISCC" "installer/starlitmoon.iss"
echo "Built: dist/v${VERSION}/StarlitMoonLauncher-Setup-${VERSION}.exe"
