#!/usr/bin/env python3
"""Generate VETGUIDE launcher icons from the Phase-0 medical-cross geometry.

Single source of truth for the raster mipmaps: the same cross vector used by
res/drawable/ic_launcher_foreground.xml (108x108 viewport), drawn white on the
deep forest green brand background (#2E7D32).

Run from the repo root:

    python3 tools/generate_icons.py

Outputs (all densities, legacy + round, plus the 512px Play Store icon):
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png
    app/src/main/playstore-icon.png
"""

from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "app" / "src" / "main" / "res"

BRAND = (46, 125, 50, 255)  # #2E7D32 — matches res/values/colors.xml
WHITE = (255, 255, 255, 255)
SS = 4  # supersampling factor for clean edges

# Medical cross in the 108x108 launcher viewport.
CROSS_V = (46, 26, 62, 82)  # x0, y0, x1, y1 of the vertical bar
CROSS_H = (32, 40, 76, 56)  # x0, y0, x1, y1 of the horizontal bar
VIEWPORT = 108

# Legacy icon: cross occupies ~64% of the height (Android legacy safe area).
LEGACY_CROSS_FRACTION = 0.64
ROUND_CROSS_FRACTION = 0.56

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def cross_rects(size: int, fraction: float) -> list[tuple[float, float, float, float]]:
    """Scale the viewport cross so its height equals `fraction` of `size`."""
    cross_h = CROSS_V[3] - CROSS_V[1]
    scale = (size * fraction) / cross_h
    origin_x = (size - VIEWPORT * scale) / 2
    origin_y = (size - VIEWPORT * scale) / 2

    def mapped(rect):
        x0, y0, x1, y1 = rect
        return (
            origin_x + x0 * scale,
            origin_y + y0 * scale,
            origin_x + x1 * scale,
            origin_y + y1 * scale,
        )

    return [mapped(CROSS_V), mapped(CROSS_H)]


def draw_rounded_icon(size: int, round_icon: bool) -> Image.Image:
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if round_icon:
        draw.ellipse((0, 0, big - 1, big - 1), fill=BRAND)
        fraction = ROUND_CROSS_FRACTION
    else:
        radius = int(big * 0.20)
        draw.rounded_rectangle((0, 0, big - 1, big - 1), radius=radius, fill=BRAND)
        fraction = LEGACY_CROSS_FRACTION
    for rect in cross_rects(big, fraction):
        draw.rectangle(rect, fill=WHITE)
    return img.resize((size, size), Image.LANCZOS)


def draw_playstore_icon(size: int = 512) -> Image.Image:
    """Play Console icon: opaque square, no rounded corners (Play applies its own mask)."""
    big = size * SS
    img = Image.new("RGBA", (big, big), BRAND)
    draw = ImageDraw.Draw(img)
    for rect in cross_rects(big, 0.60):
        draw.rectangle(rect, fill=WHITE)
    return img.resize((size, size), Image.LANCZOS).convert("RGB")


def main() -> None:
    for density, size in DENSITIES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        draw_rounded_icon(size, round_icon=False).save(out_dir / "ic_launcher.png")
        draw_rounded_icon(size, round_icon=True).save(out_dir / "ic_launcher_round.png")
        print(f"wrote mipmap-{density} ({size}px)")

    playstore = REPO / "app" / "src" / "main" / "playstore-icon.png"
    draw_playstore_icon().save(playstore)
    print(f"wrote {playstore.relative_to(REPO)} (512px)")


if __name__ == "__main__":
    main()
