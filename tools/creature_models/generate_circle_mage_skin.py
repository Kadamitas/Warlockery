#!/usr/bin/env python3
"""Composes the Circle Mage atlas from the mod owner's three reference skins.

Deterministic (no randomness). Reads the three 64x64 references that live in the
repository root only (they are never shipped):
  circle_mage_skin_1.png  navy coat, white shirt, red trim       -> arms + legs (base and outer)
  circle_mage_skin_2.png  black robe, gold/ember trim            -> torso (base) + jacket (outer)
  circle_mage_skin_3.png  grey-blue bearded wizard, pointed hat  -> head (base) + hood/hat overlay
and copies those UV islands verbatim into the standard player-skin layout, then
harmonises every source toward one scheme (deep purple-charcoal cloth, gold/ember trim,
pale face) so the result reads as a single outfit.

The atlas is 128x64: the left 64x64 is the standard skin layout, the right half holds
the modeled wizard hat's islands (brim 11x1x11 at (64,0), cone_1 5x4x5 at (108,0),
cone_2 3x4x3 at (64,16), cone_3 2x4x2 at (76,16), tip 1x3x1 at (84,16)). All player
outer layers keep their source transparency; base islands and hat islands are forced
opaque (a transparent base pixel is filled with the cloth colour and reported).

Run from anywhere: python tools/creature_models/generate_circle_mage_skin.py
"""
from __future__ import annotations

import colorsys
from pathlib import Path

from PIL import Image

WIDTH, HEIGHT = 128, 64
REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
OUTPUT = REPOSITORY_ROOT / "src/main/resources/assets/warlockery/textures/entity/circle_mage.png"
REFERENCES = {
    1: REPOSITORY_ROOT / "docs/art-source/skin-references/circle_mage_reference_1.png",
    2: REPOSITORY_ROOT / "docs/art-source/skin-references/circle_mage_reference_2.png",
    3: REPOSITORY_ROOT / "docs/art-source/skin-references/circle_mage_reference_3.png",
}

Color = tuple[int, int, int, int]
CLEAR: Color = (0, 0, 0, 0)
GOLD: Color = (210, 154, 58, 255)
GOLD_DARK: Color = (150, 104, 38, 255)
GOLD_LIGHT: Color = (242, 200, 104, 255)
EMBER: Color = (222, 110, 44, 255)

# Standard player-skin islands: name -> (u, v, w, h, d, source skin, is outer layer)
ISLANDS: dict[str, tuple[int, int, int, int, int, int, bool]] = {
    "head": (0, 0, 8, 8, 8, 3, False),
    "hood": (32, 0, 8, 8, 8, 3, True),
    "body": (16, 16, 8, 12, 4, 2, False),
    "jacket": (16, 32, 8, 12, 4, 2, True),
    "right_arm": (40, 16, 4, 12, 4, 1, False),
    "right_sleeve": (40, 32, 4, 12, 4, 1, True),
    "left_arm": (32, 48, 4, 12, 4, 1, False),
    "left_sleeve": (48, 48, 4, 12, 4, 1, True),
    "right_leg": (0, 16, 4, 12, 4, 1, False),
    "right_pants": (0, 32, 4, 12, 4, 1, True),
    "left_leg": (16, 48, 4, 12, 4, 1, False),
    "left_pants": (0, 48, 4, 12, 4, 1, True),
}
# Modeled hat islands in the right half: name -> (u, v, w, h, d)
HAT_ISLANDS: dict[str, tuple[int, int, int, int, int]] = {
    "hat_brim": (64, 0, 11, 1, 11),
    "hat_cone_1": (108, 0, 5, 4, 5),
    "hat_cone_2": (64, 16, 3, 4, 3),
    "hat_cone_3": (76, 16, 2, 4, 2),
    "hat_tip": (84, 16, 1, 3, 1),
}


def faces(u: int, v: int, w: int, h: int, d: int) -> dict[str, tuple[int, int, int, int]]:
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def face_pixels(u: int, v: int, w: int, h: int, d: int) -> list[tuple[int, int]]:
    points: list[tuple[int, int]] = []
    for fx, fy, fw, fh in faces(u, v, w, h, d).values():
        for yy in range(fy, fy + fh):
            for xx in range(fx, fx + fw):
                points.append((xx, yy))
    return points


def from_hls(h: float, l: float, s: float) -> Color:
    r, g, b = colorsys.hls_to_rgb(h % 1.0, max(0.0, min(1.0, l)), max(0.0, min(1.0, s)))
    return (round(r * 255), round(g * 255), round(b * 255), 255)


def harmonise(color: Color) -> Color:
    """Map one reference pixel toward the shared scheme. Alpha is preserved as-is."""
    r, g, b, a = color
    if a == 0:
        return CLEAR
    h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
    degrees = h * 360.0
    warm = degrees < 60.0 or degrees >= 330.0
    if warm and s >= 0.42 and l < 0.66:
        # Saturated reds/oranges/golds are trim: reds become ember, the rest gold.
        if degrees < 18.0 or degrees >= 330.0:
            return from_hls(24.0 / 360.0, max(0.44, min(l + 0.12, 0.58)), 0.72)
        return from_hls(40.0 / 360.0, max(0.42, min(l + 0.10, 0.66)), 0.70)
    if warm and s >= 0.16 and l >= 0.40:
        # Tan skin becomes pale skin; keep its relative shading.
        return from_hls(26.0 / 360.0, 0.62 + 0.36 * (l - 0.40), 0.36)
    if s < 0.16 and l >= 0.68:
        # Whites (shirt, beard) stay pale, faintly warmed so they sit with the gold.
        return from_hls(36.0 / 360.0, l, 0.10)
    if 70.0 <= degrees < 170.0 and s >= 0.3:
        # Green eyes on the robe reference: keep as an arcane accent.
        return (r, g, b, 255)
    # Everything else (greys, navy, blue-greys, blacks, blues) is cloth: deep purple-charcoal,
    # with lightness compressed so the three sources share one value range.
    cloth_l = 0.08 + 0.55 * l
    cloth_s = 0.30 if s < 0.30 else min(s, 0.45)
    return from_hls(268.0 / 360.0, cloth_l, cloth_s)


FACE_FRONT = (8, 8, 16, 16)


def harmonise_face(color: Color, xx: int, yy: int) -> Color:
    """The bare face never wears cloth: the reference's grey mouth corners and eye rims become a
    warm skin shadow instead of robe purple, so the mage does not read as having purple teeth."""
    mapped = harmonise(color)
    x0, y0, x1, y1 = FACE_FRONT
    if x0 <= xx < x1 and y0 <= yy < y1 and mapped[3] == 255:
        h, l, s = colorsys.rgb_to_hls(mapped[0] / 255.0, mapped[1] / 255.0, mapped[2] / 255.0)
        if 240.0 <= h * 360.0 < 300.0:
            return from_hls(22.0 / 360.0, 0.30 + 0.25 * l, 0.34)
    return mapped


HOOD_FRONT = (40, 8, 48, 16)


def harmonise_hood(color: Color, xx: int, yy: int) -> Color:
    """The hood overlay only frames the top of the face: any cloth pixel the reference leaves on the
    lower front face (beard fringe, mouth corners) is cleared so no purple floats beside the mouth."""
    mapped = harmonise(color)
    x0, y0, x1, y1 = HOOD_FRONT
    if x0 <= xx < x1 and y0 + 3 <= yy < y1 and mapped[3] == 255:
        h, _, _ = colorsys.rgb_to_hls(mapped[0] / 255.0, mapped[1] / 255.0, mapped[2] / 255.0)
        if 240.0 <= h * 360.0 < 300.0:
            return CLEAR
    return mapped


def load(index: int) -> Image.Image:
    path = REFERENCES[index]
    if not path.is_file():
        raise SystemExit(f"missing reference skin {path}")
    image = Image.open(path).convert("RGBA")
    if image.size != (64, 64):
        raise SystemExit(f"{path} must be 64x64, got {image.size}")
    return image


sources = {index: load(index).load() for index in REFERENCES}
atlas = Image.new("RGBA", (WIDTH, HEIGHT), CLEAR)
pixels = atlas.load()

# ------------------------------------------------------------ player islands from the references
filled_fallbacks: list[str] = []
cloth_samples: list[Color] = []
for name, (u, v, w, h, d, source, outer) in ISLANDS.items():
    src = sources[source]
    for xx, yy in face_pixels(u, v, w, h, d):
        if name == "head":
            color = harmonise_face(tuple(src[xx, yy]), xx, yy)
        elif name == "hood":
            color = harmonise_hood(tuple(src[xx, yy]), xx, yy)
        else:
            color = harmonise(tuple(src[xx, yy]))
        if color[3] == 0 and not outer:
            color = from_hls(268.0 / 360.0, 0.18, 0.32)
            filled_fallbacks.append(f"{name}@{xx},{yy}")
        pixels[xx, yy] = color
        if name == "hood" and color[3] == 255:
            cloth_samples.append(color)

# ------------------------------------------------------------ plain hand-painted face
# The owner asked for a normal face rather than the reference's beard fringe: pale skin, brown brows,
# two-pixel eyes, a nose shade and a simple mouth. The hood overlay keeps only its top rows so
# nothing floats beside the mouth.
SKIN = (222, 187, 152, 255)
SKIN_SHADE = (196, 156, 122, 255)
BROW = (92, 62, 40, 255)
EYE_WHITE = (245, 245, 245, 255)
EYE_IRIS = (58, 92, 140, 255)
MOUTH = (150, 96, 84, 255)
for yy in range(8, 16):
    for xx in range(8, 16):
        pixels[xx, yy] = SKIN
for xx in (9, 10, 13, 14):
    pixels[xx, 10] = BROW
pixels[9, 11] = EYE_WHITE
pixels[10, 11] = EYE_IRIS
pixels[13, 11] = EYE_IRIS
pixels[14, 11] = EYE_WHITE
pixels[11, 12] = SKIN_SHADE
pixels[12, 12] = SKIN_SHADE
pixels[11, 13] = SKIN_SHADE
pixels[12, 13] = SKIN_SHADE
for xx in range(11, 13):
    pixels[xx, 14] = MOUTH
for xx in range(8, 16):
    pixels[xx, 15] = SKIN_SHADE
# Forehead rows sit under the hood: cloth on the base head and a solid hood band on the overlay,
# so no skin pokes out beside the brim where the reference hood had gaps.
HOOD_BAND = from_hls(268.0 / 360.0, 0.20, 0.32)
for yy in range(8, 10):
    for xx in range(8, 16):
        pixels[xx, yy] = HOOD_BAND
        pixels[xx + 32, yy] = HOOD_BAND
for yy in range(10, 16):
    for xx in range(40, 48):
        pixels[xx, yy] = CLEAR

# ------------------------------------------------------------ modeled hat from the hood's palette
cloth_samples.sort(key=lambda c: colorsys.rgb_to_hls(c[0] / 255, c[1] / 255, c[2] / 255)[1])
median = cloth_samples[len(cloth_samples) // 2] if cloth_samples else from_hls(268 / 360, 0.3, 0.3)
_, median_l, median_s = colorsys.rgb_to_hls(median[0] / 255, median[1] / 255, median[2] / 255)
HAT = from_hls(268.0 / 360.0, median_l, median_s)
HAT_DARK = from_hls(268.0 / 360.0, median_l - 0.09, median_s)
HAT_LIGHT = from_hls(268.0 / 360.0, median_l + 0.09, median_s)
HAT_EDGE = from_hls(268.0 / 360.0, max(0.06, median_l - 0.16), median_s)


def rect(x: int, y: int, w: int, h: int, color: Color) -> None:
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            pixels[xx, yy] = color


def hline(x: int, y: int, w: int, color: Color) -> None:
    rect(x, y, w, 1, color)


def vline(x: int, y: int, h: int, color: Color) -> None:
    rect(x, y, 1, h, color)


brim = faces(*HAT_ISLANDS["hat_brim"])
fx, fy, fw, fh = brim["top"]
rect(fx, fy, fw, fh, HAT_DARK)
rect(fx + 1, fy + 1, fw - 2, fh - 2, HAT)
rect(fx + 3, fy + 3, fw - 6, fh - 6, HAT_LIGHT)
fx, fy, fw, fh = brim["bottom"]
rect(fx, fy, fw, fh, HAT_DARK)
rect(fx + 1, fy + 1, fw - 2, fh - 2, HAT_EDGE)
for side in ("right", "front", "left", "back"):
    fx, fy, fw, fh = brim[side]
    rect(fx, fy, fw, fh, GOLD_DARK)

cone1 = faces(*HAT_ISLANDS["hat_cone_1"])
fx, fy, fw, fh = cone1["top"]
rect(fx, fy, fw, fh, HAT)
rect(fx + 1, fy + 1, 3, 3, HAT_LIGHT)
fx, fy, fw, fh = cone1["bottom"]
rect(fx, fy, fw, fh, HAT_DARK)
for side in ("right", "front", "left", "back"):
    fx, fy, fw, fh = cone1[side]
    rect(fx, fy, fw, fh, HAT)
    hline(fx, fy, fw, HAT_LIGHT)
    hline(fx, fy + 2, fw, GOLD)
    hline(fx, fy + 3, fw, GOLD_DARK)
fx, fy, fw, fh = cone1["front"]
rect(fx + 2, fy + 2, 2, 2, EMBER)
pixels[fx + 2, fy + 2] = GOLD_LIGHT
fx, fy, fw, fh = cone1["back"]
vline(fx + 2, fy, 2, HAT_DARK)

for key in ("hat_cone_2", "hat_cone_3"):
    cone = faces(*HAT_ISLANDS[key])
    fx, fy, fw, fh = cone["top"]
    rect(fx, fy, fw, fh, HAT_LIGHT)
    fx, fy, fw, fh = cone["bottom"]
    rect(fx, fy, fw, fh, HAT_DARK)
    for side in ("right", "front", "left"):
        fx, fy, fw, fh = cone[side]
        rect(fx, fy, fw, fh, HAT)
        vline(fx + 1, fy, fh - 1, HAT_LIGHT)
        hline(fx, fy + fh - 1, fw, HAT_DARK)
    fx, fy, fw, fh = cone["back"]
    rect(fx, fy, fw, fh, HAT_DARK)

tip = faces(*HAT_ISLANDS["hat_tip"])
fx, fy, fw, fh = tip["top"]
rect(fx, fy, fw, fh, GOLD_LIGHT)
fx, fy, fw, fh = tip["bottom"]
rect(fx, fy, fw, fh, HAT_DARK)
for side in ("right", "front", "left", "back"):
    fx, fy, fw, fh = tip[side]
    rect(fx, fy, fw, fh, HAT_DARK)
    pixels[fx, fy] = GOLD


# ------------------------------------------------------------ self-check, then save
def verify() -> None:
    claimed: dict[tuple[int, int], str] = {}
    everything = {name: spec[:5] for name, spec in ISLANDS.items()} | HAT_ISLANDS
    outer_names = {name for name, spec in ISLANDS.items() if spec[6]}
    for name, spec in everything.items():
        for xx, yy in face_pixels(*spec):
            if not (0 <= xx < WIDTH and 0 <= yy < HEIGHT):
                raise SystemExit(f"{name} leaves the atlas at {xx},{yy}")
            owner = claimed.get((xx, yy))
            if owner is not None and owner != name:
                raise SystemExit(f"{name} overlaps {owner} at {xx},{yy}")
            claimed[(xx, yy)] = name
            if name not in outer_names and pixels[xx, yy][3] != 255:
                raise SystemExit(f"{name} transparent at {xx},{yy}")
    for yy in range(HEIGHT):
        for xx in range(WIDTH):
            if pixels[xx, yy][3] not in (0, 255):
                raise SystemExit(f"non-binary alpha at {xx},{yy}")


verify()
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
atlas.save(OUTPUT, "PNG")
print(f"Wrote {OUTPUT.relative_to(REPOSITORY_ROOT)} ({WIDTH}x{HEIGHT})")
if filled_fallbacks:
    print(f"Filled {len(filled_fallbacks)} transparent base pixels: {', '.join(filled_fallbacks[:12])}")
