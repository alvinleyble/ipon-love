#!/bin/zsh
# Render the Play Store screenshot slides → assets/brand/playstore/png/
# Recipe matches assets/brand/README.md: Edge headless at 2×, then sips
# downscale (which also flattens alpha → satisfies Play's no-transparency rule).
set -e
cd "$(dirname "$0")"

EDGE="/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
TMP="$(mktemp -d)"
mkdir -p png

for f in slides/slide-*.html; do
  n="${${f:t}%.html}"                # slide-01 … slide-08
  out="png/playstore-${n#slide-}.png"
  "$EDGE" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=2 --window-size=1080,1920 \
    --screenshot="$TMP/$n-2x.png" "file://$PWD/$f" 2>/dev/null
  sips -z 1920 1080 "$TMP/$n-2x.png" --out "$out" >/dev/null
  echo "rendered $out"
done

rm -rf "$TMP"
