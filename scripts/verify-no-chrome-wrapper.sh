#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Checking for Chrome/TWA/Custom Tabs wrapper code..."
if grep -RniE 'TrustedWebActivity|CustomTabs|androidx\.browser|com\.google\.androidbrowserhelper|setPackage\("com\.android\.chrome"|setPackage\("com\.chrome' "$ROOT/app/src"; then
  echo "ERROR: Browser-wrapper reference found."
  exit 1
fi

echo "OK: no TWA, Custom Tabs, androidx.browser, or forced Chrome package found."
echo "Primary container references:"
grep -Rni 'android\.webkit\.WebView\|<WebView' "$ROOT/app/src/main" | head -20 || true
