"""Vampire court skins: a deterministic mix of the owner's reference skins and the court design.

Reads docs/art-source/skin-references/vampire_{masculine,feminine}_reference.png (64x64 player
skins), keeps their outfit shapes and outer layers, harmonises their palette into the court scheme
(pale skin, black hair, black coat, crimson lining, white lace), and paints the court face, lapels,
shirt and brooch on top. Writes the 64x64 atlases used by VampireModel.
Run from the repository root: python tools/creature_models/generate_vampire_court_skins.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
REFERENCES = ROOT / "docs/art-source/skin-references"
ENTITY = ROOT / "src/main/resources/assets/warlockery/textures/entity"


def rgb(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


SKIN = rgb("#e9dfd8")
SKIN_SHADE = rgb("#c9b6b1")
SKIN_LIGHT = rgb("#f6efe9")
HAIR = rgb("#141319")
HAIR_SHEEN = rgb("#2c2a36")
EYE = rgb("#d8263a")
EYE_DARK = rgb("#7a0f1c")
MOUTH = rgb("#6a2434")
FANG = rgb("#f4f1ea")
COAT = rgb("#15151b")
COAT_EDGE = rgb("#26262f")
COAT_LIGHT = rgb("#34343f")
CRIMSON = rgb("#8d1424")
CRIMSON_DEEP = rgb("#5a0b17")
CRIMSON_LIGHT = rgb("#b32234")
SHIRT = rgb("#ece6dc")
SHIRT_SHADE = rgb("#c9c0b2")
GOLD = rgb("#c9a44a")
LACE = rgb("#f3eee6")
PEARL = rgb("#efe9dc")
PEARL_SHADE = rgb("#bdb3a6")
MAROON_HAIR = rgb("#4a1220")
MAROON_HAIR_LIGHT = rgb("#6e1e30")

# Standard 64x64 skin islands: (x, y, w, h)
HEAD = (0, 0, 32, 16)
HAT = (32, 0, 32, 16)
BODY = (16, 16, 24, 16)
JACKET = (16, 32, 24, 16)
RIGHT_ARM = (40, 16, 16, 16)
RIGHT_SLEEVE = (40, 32, 16, 16)
LEFT_ARM = (32, 48, 16, 16)
LEFT_SLEEVE = (48, 48, 16, 16)
RIGHT_LEG = (0, 16, 16, 16)
RIGHT_PANTS = (0, 32, 16, 16)
LEFT_LEG = (16, 48, 16, 16)
LEFT_PANTS = (0, 48, 16, 16)


def fill(image, x, y, w, h, color):
    for py in range(y, y + h):
        for px in range(x, x + w):
            image.putpixel((px, py), color)


def luminance(pixel):
    r, g, b = pixel[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def harmonise(image, island, dark_ramp, red_ramp, pale_keep=True):
    """Remap a reference island's pixels onto the court palette while keeping its shapes."""
    x, y, w, h = island
    for py in range(y, y + h):
        for px in range(x, x + w):
            pixel = image.getpixel((px, py))
            if pixel[3] == 0:
                continue
            r, g, b = pixel[:3]
            lum = luminance(pixel)
            reddish = r > g + 28 and r > b + 28
            if reddish:
                color = red_ramp[0] if lum < 60 else red_ramp[1] if lum < 110 else red_ramp[2]
            elif lum > 175 and pale_keep:
                color = SKIN_LIGHT if lum > 220 else SKIN
            else:
                color = dark_ramp[0] if lum < 30 else dark_ramp[1] if lum < 70 else dark_ramp[2]
            image.putpixel((px, py), color)


def paint_court_face(image, feminine):
    """The court face: pale skin, a hair cap over the top/sides/back, clear two-pixel eyes, a plain mouth."""
    top, bottom, right, front, left, back = (8, 0), (16, 0), (0, 8), (8, 8), (16, 8), (24, 8)
    hair = MAROON_HAIR if feminine else HAIR
    sheen = MAROON_HAIR_LIGHT if feminine else HAIR_SHEEN
    fill(image, top[0], top[1], 8, 8, hair)
    fill(image, 9, 1, 3, 2, sheen)
    fill(image, bottom[0], bottom[1], 8, 8, SKIN_SHADE)
    # sides: hair covers the upper half, skin below with a small ear mark
    for face in (right, left):
        fill(image, face[0], face[1], 8, 8, SKIN)
        fill(image, face[0], face[1], 8, 5 if feminine else 4, hair)
        fill(image, face[0] + 1, face[1] + 4, 1, 1, sheen)
        fill(image, face[0] + 3, face[1] + 5, 2, 2, SKIN_SHADE)
    fill(image, back[0], back[1], 8, 8, hair)
    fill(image, back[0] + 2, back[1] + 1, 1, 5, sheen)
    if not feminine:
        fill(image, back[0], back[1] + 6, 8, 2, SKIN_SHADE)
    # face
    fill(image, front[0], front[1], 8, 8, SKIN)
    fill(image, 8, 8, 8, 1, hair)
    fill(image, 8, 9, 1, 1, hair)
    fill(image, 15, 9, 1, 1, hair)
    # two-pixel eyes: pale sclera beside a red iris, brow shadow above
    fill(image, 9, 10, 2, 1, SKIN_SHADE)
    fill(image, 13, 10, 2, 1, SKIN_SHADE)
    image.putpixel((9, 11), SKIN_LIGHT)
    image.putpixel((10, 11), EYE)
    image.putpixel((13, 11), EYE)
    image.putpixel((14, 11), SKIN_LIGHT)
    image.putpixel((11, 13), SKIN_SHADE)
    image.putpixel((12, 13), SKIN_SHADE)
    fill(image, 10, 14, 4, 1, CRIMSON if feminine else MOUTH)
    fill(image, 8, 15, 8, 1, SKIN_SHADE)


def masculine(head_reference: Image.Image, body_reference: Image.Image) -> Image.Image:
    """Owner-directed composite: the head and hair overlay (rows 0..16) copied verbatim from one
    reference skin, everything below (body, arms, legs, all outer layers) verbatim from another."""
    image = body_reference.copy()
    image.paste(recentre_head(head_reference).crop((0, 0, 64, 16)), (0, 0))
    return image


def recentre_head(reference: Image.Image) -> Image.Image:
    """The head reference was authored with the face on the left-side island (u 16..24), so a
    verbatim copy renders the face on the side of the head. The four side faces form a cyclic
    strip, so rolling the strip by one island puts the face on the front island; the top and
    bottom islands are rotated a quarter turn to match."""
    image = reference.copy()
    for base in (0, 32):
        strip = reference.crop((base, 8, base + 32, 16))
        rolled = Image.new("RGBA", strip.size)
        rolled.paste(strip.crop((8, 0, 32, 8)), (0, 0))
        rolled.paste(strip.crop((0, 0, 8, 8)), (24, 0))
        image.paste(rolled, (base, 8))
        image.paste(reference.crop((base + 8, 0, base + 16, 8)).transpose(Image.Transpose.ROTATE_270), (base + 8, 0))
        image.paste(reference.crop((base + 16, 0, base + 24, 8)).transpose(Image.Transpose.ROTATE_90), (base + 16, 0))
    return image


def feminine(reference: Image.Image) -> Image.Image:
    image = reference.copy()
    dark = (COAT, CRIMSON_DEEP, CRIMSON)
    red = (CRIMSON_DEEP, CRIMSON, CRIMSON_LIGHT)
    for island in (BODY, JACKET, RIGHT_ARM, RIGHT_SLEEVE, LEFT_ARM, LEFT_SLEEVE):
        harmonise(image, island, dark, red)
    for island in (RIGHT_LEG, RIGHT_PANTS, LEFT_LEG, LEFT_PANTS):
        harmonise(image, island, (COAT, COAT, COAT_EDGE), red)
    # the reference head and hair overlay are kept verbatim: the owner wants the reference faces
    # gown front: white lace collar over a black corset band
    fill(image, 21, 20, 6, 2, LACE)
    fill(image, 22, 22, 4, 1, SHIRT_SHADE)
    fill(image, 20, 25, 8, 3, COAT)
    fill(image, 23, 25, 2, 3, COAT_LIGHT)
    image.putpixel((24, 24), PEARL)
    image.putpixel((23, 24), PEARL_SHADE)
    fill(image, 44, 30, 4, 2, SKIN)
    fill(image, 36, 62, 4, 2, SKIN)
    return image


def snap_alpha(image: Image.Image) -> Image.Image:
    """Entity atlases must be fully opaque or fully clear; the reference hair overlay has a few
    half-transparent anti-aliased pixels, so alpha is snapped at the midpoint."""
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            pixels[x, y] = (r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
    return image


def load(name: str) -> Image.Image:
    image = Image.open(REFERENCES / name).convert("RGBA")
    if image.size != (64, 64):
        raise SystemExit(f"{name} must be a 64x64 skin, found {image.size}")
    return image


if __name__ == "__main__":
    snap_alpha(masculine(load("vampire_masculine_head_reference.png"), load("vampire_masculine_body_reference.png"))).save(ENTITY / "vampire_masculine.png")
    snap_alpha(feminine(load("vampire_feminine_reference.png"))).save(ENTITY / "vampire_feminine.png")
    print("vampire court skins written")
