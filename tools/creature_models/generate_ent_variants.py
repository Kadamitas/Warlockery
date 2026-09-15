"""Dedicated Ent atlas generator: one 256x128 atlas per wood variant.

Deterministic pixel painting against the EntModel UV layout. ent.png is the oak atlas; every
other variant writes ent_<variant>.png with its own bark and canopy palette.
Run from the repository root: python tools/creature_models/generate_ent_variants.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ENTITY = ROOT / "src/main/resources/assets/warlockery/textures/entity"
WIDTH, HEIGHT = 256, 128


def rgb(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# (bark, bark dark, bark light, leaf, leaf dark, leaf light)
VARIANTS = {
    "oak": ("#6f4d2c", "#4d3419", "#946f48", "#4f8f2b", "#38691e", "#6fb041"),
    "birch": ("#e6e0d3", "#3a3733", "#f6f2ea", "#7db34a", "#5b8a34", "#a2cf62"),
    "spruce": ("#4b3120", "#2f1e10", "#6d4c33", "#2f5c3a", "#1f4128", "#3f7a4b"),
    "jungle": ("#7d5c36", "#573f22", "#a07d4d", "#3f9c3b", "#2a7128", "#5fbd50"),
    "dark_oak": ("#3f2b18", "#28190c", "#5c4128", "#2e6a2a", "#1f4b1d", "#3f8636"),
    "acacia": ("#8c6b4b", "#654f37", "#ab8c6c", "#6f9c2f", "#527a21", "#8fbc41"),
    "mangrove": ("#6f3b2e", "#4d271e", "#93574a", "#4b8b3f", "#356a2e", "#63a652"),
    "cherry": ("#402c2c", "#2a1b1b", "#5c4141", "#f0a6c0", "#d585a3", "#f9cbd9"),
    "pale_oak": ("#d8d2c6", "#b1aa9e", "#ece7dc", "#8fa27a", "#6e7f5c", "#a9b894"),
}

# Box islands as (u, v, w, h, d, kind); kind is "bark", "twig", "leaf", or "knot".
ISLANDS = [
    (0, 0, 16, 11, 10, "bark"),      # trunk base
    (54, 0, 14, 18, 10, "bark"),     # split trunk
    (104, 0, 8, 8, 2, "knot"),       # hollow knot face plate
    (126, 0, 3, 3, 2, "knot"),       # knot core
    (140, 0, 8, 10, 2, "bark"),      # bark plate
    (164, 0, 10, 6, 2, "bark"),      # trunk base plate
    (190, 0, 8, 8, 9, "bark"),       # high shoulder
    (226, 0, 5, 15, 5, "bark"),      # right arm
    (0, 26, 4, 16, 4, "bark"),       # right forearm
    (18, 26, 7, 7, 4, "twig"),       # right fan
    (42, 26, 2, 9, 2, "twig"),
    (52, 26, 2, 8, 2, "twig"),
    (62, 26, 8, 7, 8, "bark"),       # low shoulder
    (96, 26, 6, 13, 6, "bark"),      # left arm
    (122, 26, 4, 17, 4, "bark"),     # left forearm
    (140, 26, 7, 8, 4, "twig"),      # left fan
    (164, 26, 2, 10, 2, "twig"),
    (174, 26, 2, 7, 2, "twig"),
    (184, 26, 10, 8, 8, "bark"),     # crown
    (222, 26, 3, 13, 3, "twig"),     # crown left fork
    (236, 26, 2, 9, 2, "twig"),
    (0, 50, 3, 11, 3, "twig"),       # crown right fork
    (14, 50, 2, 8, 2, "twig"),
    (24, 50, 3, 12, 3, "twig"),      # crown reach
    (38, 50, 7, 12, 7, "bark"),      # right root leg
    (68, 50, 10, 4, 15, "bark"),     # right root foot
    (120, 50, 3, 3, 9, "twig"),      # right root spurs
    (146, 50, 8, 12, 8, "bark"),     # left root leg
    (180, 50, 12, 4, 13, "bark"),    # left root foot
    (0, 76, 3, 3, 9, "twig"),        # left root spurs
    (28, 92, 14, 7, 12, "leaf"),     # high canopy
    (0, 112, 10, 5, 9, "leaf"),
    (84, 92, 13, 6, 11, "leaf"),     # left canopy
    (42, 112, 8, 4, 8, "leaf"),
    (136, 92, 12, 6, 10, "leaf"),    # right canopy
    (78, 112, 7, 4, 7, "leaf"),
]


def faces(u, v, w, h, d):
    """Yield (x, y, width, height, face) rectangles for a standard box island."""
    yield u + d, v, w, d, "top"
    yield u + d + w, v, w, d, "bottom"
    yield u, v + d, d, h, "right"
    yield u + d, v + d, w, h, "front"
    yield u + d + w, v + d, d, h, "left"
    yield u + 2 * d + w, v + d, w, h, "back"


def paint_bark(image, x, y, w, h, face, palette):
    bark, dark, light = palette
    for py in range(y, y + h):
        for px in range(x, x + w):
            grain = (px * 3 + (py // 3) * 5) % 7
            if face in ("top", "bottom"):
                # ring pattern on cut ends
                ring = (abs(px - (x + w // 2)) + abs(py - (y + h // 2))) % 3
                color = dark if ring == 0 else (light if ring == 2 and (px + py) % 2 else bark)
            elif grain == 0:
                color = dark
            elif grain == 4 and py % 2:
                color = light
            else:
                color = bark
            image.putpixel((px, py), color)


def paint_twig(image, x, y, w, h, face, palette):
    bark, dark, light = palette
    for py in range(y, y + h):
        for px in range(x, x + w):
            image.putpixel((px, py), dark if (px + py * 2) % 5 == 0 else bark)
    if w >= 2 and h >= 2:
        image.putpixel((x, y), light)


def paint_leaf(image, x, y, w, h, face, palette):
    leaf, dark, light = palette
    for py in range(y, y + h):
        for px in range(x, x + w):
            cell = (px * 5 + py * 3) % 9
            if cell in (0, 4):
                color = dark
            elif cell in (2, 7):
                color = light
            else:
                color = leaf
            image.putpixel((px, py), color)


def paint_knot(image, x, y, w, h, face, palette):
    bark, dark, light = palette
    paint_bark(image, x, y, w, h, face, palette)
    if face == "front" and w >= 8 and h >= 8:
        # two hollow eyes and a mouth crack, readable at play distance
        for ex in (x + 1, x + 5):
            image.putpixel((ex, y + 2), dark)
            image.putpixel((ex + 1, y + 2), dark)
            image.putpixel((ex, y + 3), (8, 6, 4, 255))
            image.putpixel((ex + 1, y + 3), (8, 6, 4, 255))
        for mx in range(x + 2, x + 6):
            image.putpixel((mx, y + 6), (8, 6, 4, 255))
    elif face == "front":
        for py in range(y, y + h):
            for px in range(x, x + w):
                image.putpixel((px, py), (8, 6, 4, 255))


def paint(variant):
    bark, dark, light, leaf, leaf_dark, leaf_light = (rgb(c) for c in VARIANTS[variant])
    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    painters = {"bark": paint_bark, "twig": paint_twig, "leaf": paint_leaf, "knot": paint_knot}
    for u, v, w, h, d, kind in ISLANDS:
        palette = (leaf, leaf_dark, leaf_light) if kind == "leaf" else (bark, dark, light)
        for x, y, fw, fh, face in faces(u, v, w, h, d):
            painters[kind](image, x, y, fw, fh, face, palette)
    return image


if __name__ == "__main__":
    for variant in VARIANTS:
        name = "ent.png" if variant == "oak" else f"ent_{variant}.png"
        paint(variant).save(ENTITY / name)
    print("ent variant atlases written")
