#!/usr/bin/env python3
"""Generate all Android launcher icons for the Anikage app — official logo.

Design source: scripts/brand/logo.png — the official Anikage app mark
(1254x1254, stylized purple-gradient "A" on a pure black background).

The letter is extracted from the black background (alpha = max RGB channel,
colors un-premultiplied), then rendered into every launcher icon format:

  mipmap-{mdpi..xxxhdpi}/ic_launcher.png             legacy rounded-square
  mipmap-{mdpi..xxxhdpi}/ic_launcher_round.png       legacy circle
  mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png  adaptive foreground
                                                     (A on transparent, safe-
                                                     zone scaled; also the
                                                     monochrome layer)

Adaptive background color: values/colors.xml -> ic_launcher_background
(#000000, matching the logo's own background).

Measured on the 1254px source: letter bbox (223,154)-(1066,1106), max opaque
radius 47.2% of canvas. Legacy square keeps full official proportions (all
corners clear the 17.32%-radius mask arcs); legacy round scales to 97.5% so
the letter's extremes stay inside the circle; adaptive foreground scales so
the letter's max radius is 29.8% of the 108dp canvas (inside the 33dp safe
zone — survives every launcher mask shape).
"""
from PIL import Image, ImageDraw
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
RES = PROJECT / "app/src/main/res"
BRAND = PROJECT / "scripts/brand/logo.png"

DENSITIES = {   # bucket -> legacy icon px
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}
FOREGROUNDS = { # bucket -> adaptive foreground px (108dp canvas)
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}

LEGACY_CORNER_RATIO = 0.1732        # Material rounded-square corner radius
LETTER_MAX_RADIUS = 0.472           # measured on the source logo
ROUND_SAFETY = 0.975                # shrink for the circular legacy mask
ADAPTIVE_RADIUS = 0.298             # target max radius on the 108dp canvas


def load_logo():
    img = Image.open(BRAND).convert("RGB")
    if img.size[0] != img.size[1]:
        raise SystemExit(f"expected square logo, got {img.size}")
    return img


def extract_letter():
    """Extract the gradient 'A' as RGBA: alpha = max(r,g,b) (black bg -> 0),
    RGB un-premultiplied so gradient colors stay true at the edges."""
    src = load_logo()
    w, h = src.size
    out = Image.new("RGBA", (w, h))
    spx, opx = src.load(), out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = spx[x, y]
            a = max(r, g, b)
            if a <= 1:
                opx[x, y] = (0, 0, 0, 0)
            else:
                k = 255.0 / a
                opx[x, y] = (
                    min(255, int(r * k)),
                    min(255, int(g * k)),
                    min(255, int(b * k)),
                    a,
                )
    return out


def paste_center(canvas, img, scale):
    """Paste img centered on canvas, sized at `scale` of the canvas size."""
    size = canvas.size[0]
    new = int(round(size * scale))
    scaled = img.resize((new, new), Image.LANCZOS)
    canvas.paste(scaled, ((size - new) // 2, (size - new) // 2), scaled)
    return canvas


def make_foreground(size_px):
    """Adaptive foreground: A scaled so max radius = 29.8% of canvas."""
    letter = extract_letter()
    k = ADAPTIVE_RADIUS / LETTER_MAX_RADIUS
    return paste_center(Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0)), letter, k)


def make_legacy(size_px, round_shape):
    """Legacy icon: full official design (A on black) masked to shape."""
    logo = load_logo()
    if round_shape:
        # shrink content slightly so the letter's extremes stay inside the circle
        canvas = Image.new("RGB", (size_px, size_px), (0, 0, 0))
        small = int(round(size_px * ROUND_SAFETY))
        resized = logo.resize((small, small), Image.LANCZOS)
        canvas.paste(resized, ((size_px - small) // 2, (size_px - small) // 2))
    else:
        canvas = logo.resize((size_px, size_px), Image.LANCZOS)
    mask = Image.new("L", (size_px, size_px), 0)
    d = ImageDraw.Draw(mask)
    if round_shape:
        d.ellipse((0, 0, size_px - 1, size_px - 1), fill=255)
    else:
        d.rounded_rectangle(
            (0, 0, size_px - 1, size_px - 1),
            radius=int(size_px * LEGACY_CORNER_RATIO), fill=255,
        )
    out = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    out.paste(canvas, (0, 0), mask)
    return out


def main():
    if not BRAND.exists():
        raise SystemExit(f"missing brand asset: {BRAND}")
    written = []
    for bucket, size in DENSITIES.items():
        folder = RES / f"mipmap-{bucket}"
        folder.mkdir(parents=True, exist_ok=True)
        for name, img in (
            ("ic_launcher.png", make_legacy(size, round_shape=False)),
            ("ic_launcher_round.png", make_legacy(size, round_shape=True)),
            ("ic_launcher_foreground.png", make_foreground(FOREGROUNDS[bucket])),
        ):
            img.save(folder / name, optimize=True)
            written.append(f"mipmap-{bucket}/{name}")
    print(f"Wrote {len(written)} icon files")


if __name__ == "__main__":
    main()
