# Hacker News — Design System

Design tokens and brand assets extracted from [news.ycombinator.com](https://news.ycombinator.com/) on 2026-06-24.

Hacker News is a minimalist, content-first design: a single orange brand color, a warm off-white page, system-safe Verdana type, and zero ornamentation (no shadows, no rounded corners, no gradients). Use it when you want maximum density and legibility with an unmistakably "indie tech" feel.

## Assets

| File | Description |
|---|---|
| [../assets/homepage.png](../assets/homepage.png) | Homepage screenshot |
| [../assets/logo.svg](../assets/logo.svg) | "Y" wordmark logo (orange square, white Y) |
| [../assets/favicon.ico](../assets/favicon.ico) | Favicon |
| [../assets/design-tokens.json](../assets/design-tokens.json) | Structured design tokens |

## Colors

| Token | Hex | Usage |
|---|---|---|
| `brand.primary` | `#ff6600` | Header bar, logo background, the single accent color |
| `background.default` | `#f6f6ef` | Page background (warm off-white / beige) |
| `background.light` | `#ffffff` | Form fields, contrast surfaces |
| `text.primary` | `#000000` | Story titles, links, body text |
| `text.secondary` / `text.muted` | `#828282` | Metadata, subtext, ranks, visited links, timestamps |
| `text.dark` | `#222222` | Comment body text |
| `text.onBrand` | `#000000` | Text on the orange header (black, not white) |
| `border.logo` | `#ffffff` | 1px white border around the logo mark |

> The entire interface is built from **one** brand color (`#ff6600`). There is no secondary palette, no semantic error/success colors — state is conveyed through text and the gray scale only.

## Typography

- **Font family:** `Verdana, Geneva, sans-serif` — a system font stack, no web fonts loaded. Verdana is chosen for its high legibility at small sizes.
- **Weights:** `400` (regular) for nearly everything; `700` (bold) reserved for the "Hacker News" wordmark and a few emphasis cases.

| Size token | Value | Usage |
|---|---|---|
| `xs` | `7pt` (~9.3px) | Subtext: points, author, time, comment count |
| `base` | `10pt` (~13.3px) | Story titles, links, nav, body |
| `md` | `12pt` | Occasional headings |

Line-height is browser default (`normal`); the header uses a tight `12px`.

## Spacing

Base unit is **4px**, applied sparingly. HN uses table-cell padding (typically 2px) rather than generous whitespace; the layout is intentionally dense.

`4 · 8 · 12 · 16 · 20 · 24 · 28 · 32` px

## Border Radius

**None.** Every element has `border-radius: 0`. Sharp corners are core to the brand's flat, utilitarian look. Do not introduce rounded corners.

## Components

### Header bar
- Background `#ff6600`, full content width
- Logo (`logo.svg`) at 18×18 with a `1px solid #ffffff` border, left-aligned
- "Hacker News" wordmark in bold black, followed by pipe-separated nav links
- Padding ~2px

### Navigation
- Inline text links: `new | past | comments | ask | show | jobs | submit`
- Black (`#000000`), `10pt`, regular weight, separated by ` | `
- No underline; current section may render in a muted tone

### Story row
- Left: gray rank number (`#828282`)
- Center: black title link (`10pt`) + small gray domain in parentheses
- Below: gray `7pt` subtext line (points · author · time · comments)

### Links
- Default `#000000`, **no underline**
- Visited `#828282`
- Hover behavior is minimal — color only, no transitions

### Page container
- `#f6f6ef` background table, constrained to ~85% width, centered

## Logo Usage

`logo.svg` is the iconic Y Combinator mark: a `#ff6600` (`#f60`) square with a white **Y** glyph, 18×18 viewport. Always present it with a `1px solid #ffffff` border on the orange header. On light backgrounds the orange square provides its own contrast; do not place it on busy imagery. Keep it small — it is a mark, not a hero element.

## Visual Style Summary

Hacker News is the archetype of function-over-form web design: one bold orange accent, a warm paper-like off-white canvas, dense Verdana text, and total absence of decoration. There are no shadows, gradients, rounded corners, or animations. Information density and instant legibility are the priorities. Reproducing this brand means resisting modern polish — keep it flat, keep it tight, and let the single orange do all the talking.
