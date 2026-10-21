#!/usr/bin/env bash
# Zet MPEG-TS (aan elkaar geplakte HLS-stukken) om naar MP4, zonder hercodering.
# Vereist: ffmpeg  (brew install ffmpeg)

set -euo pipefail

if [ $# -eq 0 ]; then
  echo "Gebruik: $0 <bestand.ts> [meer bestanden]"
  echo "Voor een hele map:"
  echo "  for f in ~/Downloads/Schatkamer/*.ts; do $0 \"\$f\"; done"
  exit 1
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg ontbreekt. Installeer met: brew install ffmpeg" >&2
  exit 1
fi

KEEP_TS=0
[ -n "REMUX_KEEP_TS" ] && KEEP_TS=1

status=0
for src in "$@"; do
  if [ ! -f "$src" ]; then
    echo "overslaan (bestaat niet): $src" >&2
    status=1
    continue
  fi

  case "$src" in
    *.mp4) echo "al mp4, overslaan: $src"; continue ;;
  esac

  base=$(basename "$src" .ts)
  dir=$(dirname "$src")
  dst="$dir/$base.mp4"

  echo "remux: $src -> $dst"
  if ffmpeg -hide_banner -loglevel error -y -i "$src" -c copy "$dst"; then
    if [ "$KEEP_TS" -ne 1 ]; then
      rm -f "$src"
    fi
    size=$(du -h "$dst" | cut -f1)
    echo "  klaar ($size)"
  else
    echo "  MISLUKT" >&2
    status=1
  fi
done

exit $status
