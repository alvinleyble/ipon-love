# Love, Ipon — Brand Assets

Source-of-truth design files for the app's icon and logo. These are **not**
bundled into the APK (they live at the repo root, not `app/src/main/assets/`).
The files the app actually ships are in `app/src/main/res/` — the copies under
`android/` here are for reference/archival.

## The mark

The **Heart-Wallet**: a wallet whose silhouette *is* a heart, split by a fold
crease with a clasp. Two-tone "Signature" execution — blush upper body, rose
lower flap, plum outline.

| Token | Hex | Use |
|-------|------|-----|
| Plum   | `#8B2A57` | outline & ink |
| Rose   | `#EA8AB3` | lower flap |
| Blush  | `#FBE5EE` | upper body |
| Cream  | `#FFF7F4` | knockout / cash |
| Dark plum bg | `#3A1727` → `#1E0C15` | launcher tile gradient |

## Layout

```
svg/        Editable vector sources (64-grid; play-512 is full-bleed)
  heart-wallet-signature.svg   Two-tone mark, transparent bg (the primary logo)
  heart-wallet-mono.svg        Single-colour stencil (themed icons / nav)
  heart-wallet-tile.svg        Cream knockout on gradient (earlier tile study)
  heart-wallet-play-512.svg    Play Store listing icon, dark two-tone, no rounding

png/        Rendered raster
  play-icon-512.png            512×512 opaque — upload to Play Console (app icon)
  feature-graphic-1024x500.png 1024×500 opaque — Play Console feature graphic
  launcher-preview.png         How the launcher/splash render (reference only)

android/    Reference copies of the live res/ drawables (see paths below)
  ic_launcher_foreground.xml   app/src/main/res/drawable/
  ic_launcher_background.xml   app/src/main/res/drawable/
  ic_launcher_monochrome.xml   app/src/main/res/drawable/
  ic_heart_wallet.xml          app/src/main/res/drawable/  (in-app + login logo)
  ic_splash_logo.xml           app/src/main/res/drawable/  (splash, transparent bg)
  ic_launcher.xml              app/src/main/res/mipmap-anydpi-v26/
  ic_launcher_round.xml        app/src/main/res/mipmap-anydpi-v26/

showcase/   Design exploration pages (open in a browser)
  icon-directions.html         The original 4 directions
  heart-wallet.html            The chosen direction, developed
  feature-graphic.html         Render source for the Play feature graphic

phone-screenshots/  Raw device captures (Rose dark theme) used by playstore/

playstore/  Play Store listing screenshots (1080×1920, serif brand voice)
  slides/slides.css            Shared design system (plum bg, Baskerville, rose accents)
  slides/slide-01…08.html      One page per slide; copy lives here
  render.sh                    Renders all 8 → png/ (Edge headless 2× + sips)
  png/playstore-01…08.png      Upload these to Play Console (phone screenshots)
```

## Regenerating the Play feature graphic

`showcase/feature-graphic.html` is the source. Render at 2× and downscale for
crisp text (macOS built-ins only — no CLI converter needed):

```bash
"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge" \
  --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=1024,500 --screenshot=fg-2x.png \
  "file://$PWD/showcase/feature-graphic.html"
sips -z 500 1024 fg-2x.png --out png/feature-graphic-1024x500.png
```

`sips` flattens the alpha channel, so the output satisfies Play's
"no transparency" rule automatically.

## Regenerating the Play Store screenshots

Edit copy/layout in `playstore/slides/`, then `./playstore/render.sh` — same
2×-then-`sips` recipe as the feature graphic, one Edge invocation per slide.
Outputs are Play-compliant (exactly 1080×1920, alpha flattened, ≪8 MB).
Slide order = gallery order: hero, combined view, debts, goals, budgets,
donut, flow, calendar. New raw captures go in `phone-screenshots/` (Rose dark
theme; the Accounts capture is the only one whose device status bar needs the
`crop-statusbar` class in its slide).

## Regenerating the Play Store PNG

The PNGs were rendered from the SVGs with headless Chromium (no CLI converter
was installed). Any of these also work:

```bash
rsvg-convert -w 512 -h 512 svg/heart-wallet-play-512.svg -o png/play-icon-512.png
inkscape svg/heart-wallet-play-512.svg -w 512 -h 512 -o png/play-icon-512.png
magick -background none svg/heart-wallet-play-512.svg -resize 512x512 png/play-icon-512.png
```

## Editing note

`android/` here are **copies**. If you change an icon, edit the file in
`app/src/main/res/…` (that's what ships) and re-copy it here, or vice-versa —
keep the two in sync.
