#!/usr/bin/env bash
# Bouwt de extensie als zip en als ondertekende CRX3 in ../dist/.
set -euo pipefail

cd "$(dirname "$0")"

VERSION=$(node -p "JSON.parse(require('fs').readFileSync('manifest.json','utf8')).version")
DIST="$(cd .. && pwd)/dist"
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/vendor" "$STAGE/icons" "$DIST"

cp manifest.json background.js content.js overlay.css popup.html popup.js offscreen.html offscreen.js "$STAGE"/
cp vendor/mux.min.js vendor/LICENSE-muxjs.txt "$STAGE/vendor"/
cp icons/icon16.png icons/icon32.png icons/icon48.png icons/icon128.png "$STAGE/icons"/

ZIP="$DIST/schatkamer-downloader-extension-v${VERSION}.zip"
CRX="$DIST/schatkamer-downloader-extension-v${VERSION}.crx"

rm -f "$ZIP" "$CRX"
( cd "$STAGE" && zip -X -q -r "$ZIP" manifest.json background.js content.js overlay.css popup.html popup.js offscreen.html offscreen.js vendor icons )

node pack-crx.mjs pack "$ZIP" "$CRX"
node pack-crx.mjs verify "$CRX"

echo
unzip -l "$ZIP"
