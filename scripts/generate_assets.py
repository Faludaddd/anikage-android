#!/usr/bin/env python3
"""Generate all Android launcher icons for the Anikage app — official Anikage branding.

Design: the official anikage.cc app icon — the white rounded "A" monogram
on the flat #0D0D0D background, taken directly from Anikage's own
apple-touch-icon (180x180). This replaces the earlier "original
anime-style" generated art so the launcher icon matches Anikage's real
brand identity.

Source asset: scripts/brand/apple-touch-icon.png
  (fetched with: curl -o apple-touch-icon.png https://anikage.cc/apple-touch-icon.png
   — static assets on anikage.cc answer 200 OK without the Cloudflare
   challenge that blocks the API endpoints.)

Outputs, all under app/src/main/res/:
  mipmap-{mdpi..xxxhdpi}/ic_launcher.png           legacy rounded-square
  mipmap-{mdpi..xxxhdpi}/ic_launcher_round.png     legacy circle
  mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png  adaptive foreground
                                                    (white A on transparent,
                                                    also used as the
                                                    monochrome layer)

The adaptive background color lives in values/colors.xml
(ic_launcher_background = #0D0D0D).
"""
from PIL import Image, ImageDraw
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
RES = PROJECT / "app/src/main/res"
BRAND = PROJECT / "scripts/brand/apple-touch-icon.png"

# --- measured constants from the 180x180 official icon -------------------
OFFICIAL_BG = (13, 13, 13)          # #0D0D0D
LETTER_MAX_RADIUS = 80.7            # px from canvas center (180px canvas)
SAFE_RADIUS_RATIO = 0.298           # letter max radius / canvas size on the
                                    # adaptive foreground. The Android safe
                                    # zone is 33dp of a 108dp canvas = 0.3056;
                                    # we sit just inside it so no launcher
                                    # mask shape (circle worst case) clips the A.
LEGACY_CORNER_RATIO = 0.1732        # Material rounded-square corner radius

DENSITIES = {   # bucket -> legacy icon px
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}
FOREGROUNDS = { # bucket -> adaptive foreground px (108dp canvas)
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}


def load_official():
    img = Image.open(BRAND).convert("RGBA")
    assert img.size == (180, 180), f"expected 180x180, got {img.size}"
    return img


def extract_letter():
    """Return the white 'A' as an RGBA image (white RGB, anti-aliased alpha
    derived from luminance: bg 13 -> 0, white 255 -> 255)."""
    src = load_official()
    lum = src.convert("L")
    alpha = lum.point(lambda v: max(0, min(255, int((v - 13) * 255 / (255 - 13)))))
    letter = Image.new("RGBA", src.size, (255, 255, 255, 0))
    letter.putalpha(alpha)
    return letter


def make_foreground(size_px):
    """Adaptive foreground: white A centered, scaled so its farthest opaque
    pixel sits inside the 66dp safe-zone circle."""
    letter = extract_letter()
    bbox = letter.getbbox()          # (33, 25, 146, 154) on the 180 canvas
    crop = letter.crop(bbox)
    k = (SAFE_RADIUS_RATIO * size_px) / LETTER_MAX_RADIUS
    new_w, new_h = max(1, round(crop.width * k)), max(1, round(crop.height * k))
    scaled = crop.resize((new_w, new_h), Image.LANCZOS)
    canvas = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    canvas.paste(scaled, ((size_px - new_w) // 2, (size_px - new_h) // 2), scaled)
    return canvas


def make_legacy(size_px, round_shape):
    """Legacy icon: the official design (bg + A at official proportions),
    either rounded-square or circle with transparency outside the shape."""
    official = load_official().resize((size_px, size_px), Image.LANCZOS)
    mask = Image.new("L", (size_px, size_px), 0)
    d = ImageDraw.Draw(mask)
    if round_shape:
        d.ellipse((0, 0, size_px - 1, size_px - 1), fill=255)
    else:
        r = int(size_px * LEGACY_CORNER_RATIO)
        d.rounded_rectangle((0, 0, size_px - 1, size_px - 1), radius=r, fill=255)
    out = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    out.paste(official, (0, 0), mask)
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
            written.append(f"mipmap-{bucket}/{name} ({img.width}x{img.height})")
    print(f"Wrote {len(written)} icon files:")
    for w in written:
        print(f"  {w}")


if __name__ == "__main__":
    main()
