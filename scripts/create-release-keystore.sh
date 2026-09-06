#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-support-clue-release.jks}"
ALIAS="${2:-supportclue}"

echo "Creating production signing keystore: $OUT"
echo "You will be asked to choose a strong keystore/key password."
keytool -genkeypair \
  -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000

echo
echo "SHA-256 fingerprint (copy this into website/.well-known/assetlinks.json):"
keytool -list -v -keystore "$OUT" -alias "$ALIAS" | grep -E 'SHA256:' || true

echo
echo "IMPORTANT: Back up this keystore securely. Future APK updates must use the same key."
