# Apex logo package

Production assets adapted from the approved retro pixel concept.
The stepped A and blue crossbar are editable vector paths with solid, exact colors.
The wordmark uses **Outfit ExtraBold (800)** as a close match to the concept's
smooth lettering. The generated reference did not identify an exact font;
this is a deliberate reconstruction, not an exact font extraction.
Every wordmark is converted to paths, so recipients do not need the font installed.

## Choose a file

| Use | File |
| --- | --- |
| Website logo on a light surface | `svg/apex-logo-light.svg` |
| Website logo on a dark surface | `svg/apex-logo-dark.svg` |
| Red website logo on a light surface | `svg/apex-logo-red-light.svg` |
| Red website logo on a dark surface | `svg/apex-logo-red-dark.svg` |
| Symbol without text | `svg/apex-mark-light.svg` or `apex-mark-dark.svg` |
| Red symbol without text | `svg/apex-mark-red-light.svg` or `apex-mark-red-dark.svg` |
| Single-color printing | `svg/apex-logo-black.svg` or `apex-logo-white.svg` |
| Transparent logo for presentations | `png/apex-logo-light.png` or `apex-logo-dark.png` |
| Transparent square symbol | `png/apex-mark-light.png` or `apex-mark-dark.png` |
| Browser tab icon, automatic light/dark colors | `favicon/favicon.svg` |
| Red browser tab icon, automatic light/dark colors | `favicon/favicon-red.svg` |
| Legacy browser icon | `favicon/favicon.ico` |
| iOS home-screen icon | `favicon/apple-touch-icon.png` |
| Android/PWA icon assets | `favicon/icon-192.png` and `favicon/icon-512.png` |
| Light and dark comparison | `apex-preview.png` |

All logo and symbol SVGs and PNGs have transparent backgrounds. “Light” means
designed for use **on a light background**, and “dark” means **on a dark background**.
The preview and home-screen PNGs intentionally have opaque backgrounds.

PNG wordmarks are 2400 x 1215 pixels; standalone symbols are 1024 x 1024.
The ICO contains 16, 32, 48 and 64 pixel images. Matching individual PNGs are included.
The small favicon is optically simplified for legibility and is not a literal
downscale of the large symbol. The home-screen icons use the full mark.

## Colors

| Element | Light surfaces | Dark surfaces |
| --- | --- | --- |
| A | `#4C75A3` | `#86ADD6` |
| Crossbar | `#86ADD6` | `#B8D4EF` |
| Wordmark | `#17212E` | `#F1F5F9` |
| Suggested background | `#F5F7FA` | `#10151C` |

Use the provided dark version rather than applying an automatic invert filter.
The red variants reuse the same vector shape and Outfit wordmark as the original blue files.
Keep the proportions intact. Use the symbol alone where the full wordmark is too
small to read. Maintain clear space of approximately one crossbar-square width
around the artwork; leave room beyond the file's built-in canvas padding as needed.

## Website favicon setup

Copy the favicon files into the React project's public directory, then add:

```html
<link rel="icon" href="/favicon.svg" type="image/svg+xml" />
<link rel="icon" href="/favicon.ico" sizes="16x16 32x32 48x48 64x64" />
<link rel="apple-touch-icon" href="/apple-touch-icon.png" />
```

The adaptive SVG responds to the browser's color preference. Explicit light and
dark SVG favicon files are also included for an application-controlled theme.
The home-screen images are regular icons, not maskable icons. A PWA manifest is
not included because the app's name, start URL and hosting path should be configured
in the actual application.

## Source and rebuilding

- `source/build_vectors.py`: generates SVG path masters; requires Python and fontTools.
- `source/export_assets.cjs`: renders PNGs and packages the ICO; requires Node and Sharp.
- `source/Outfit-Variable.ttf`: original open-source font used to outline the wordmark.
- `source/OFL-Outfit.txt`: font license; retain it when redistributing the font.

Font source: https://github.com/google/fonts/tree/main/ofl/outfit

Run the Python generator, then run the Node exporter with `APEX_NODE_MODULES`
set to the directory containing the Sharp package. The fontTools version used
was 4.64.0. SVG logo masters contain no linked images, external fonts or scripts.

This package contains branding assets only; no application code was created.
