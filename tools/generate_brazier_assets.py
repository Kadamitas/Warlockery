"""Dedicated brazier generator: standing iron fire bowl model, block state, and textures.

Deterministic; writes only the brazier block models/blockstate and the brazier block textures.
Run from the repository root: python tools/generate_brazier_assets.py
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BLOCK_MODELS = ROOT / "src/main/resources/assets/warlockery/models/block"
BLOCKSTATES = ROOT / "src/main/resources/assets/warlockery/blockstates"
TEXTURES = ROOT / "src/main/resources/assets/warlockery/textures/block"


def cube(frm, to, texture, cull=True):
    faces = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        faces[face] = {"texture": texture}
    if cull:
        if frm[1] == 0:
            faces["down"]["cullface"] = "down"
        if to[1] == 16:
            faces["up"]["cullface"] = "up"
        if frm[2] == 0:
            faces["north"]["cullface"] = "north"
        if to[2] == 16:
            faces["south"]["cullface"] = "south"
        if frm[0] == 0:
            faces["west"]["cullface"] = "west"
        if to[0] == 16:
            faces["east"]["cullface"] = "east"
    return {"from": list(frm), "to": list(to), "faces": faces}


def flame_plane(angle):
    """A zero-thickness crossed plane above the coal bed, textured with the transparent flame sheet."""
    return {
        "from": [2.5, 11.5, 8], "to": [13.5, 16, 8],
        "rotation": {"origin": [8, 13.75, 8], "axis": "y", "angle": angle, "rescale": True},
        "shade": False,
        "faces": {
            "north": {"texture": "#flame", "uv": [0, 0, 16, 16]},
            "south": {"texture": "#flame", "uv": [0, 0, 16, 16]},
        },
    }


def rotated(frm, to, texture, axis, angle, origin):
    element = cube(frm, to, texture, cull=False)
    element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle, "rescale": True}
    return element


def bowl_elements():
    metal = "#metal"
    elements = [
        # four claw feet and a base ring
        cube((3, 0, 3), (5, 1, 5), metal),
        cube((11, 0, 3), (13, 1, 5), metal),
        cube((3, 0, 11), (5, 1, 13), metal),
        cube((11, 0, 11), (13, 1, 13), metal),
        cube((5, 1, 5), (11, 2, 11), metal),
        # slender stem and collar
        cube((6.5, 2, 6.5), (9.5, 8, 9.5), metal),
        cube((5.5, 7.5, 5.5), (10.5, 8.5, 10.5), metal),
        # shallow bowl: underside, wide belly, thin open rim
        cube((5, 8.5, 5), (11, 10, 11), metal),
        cube((3.5, 10, 3.5), (12.5, 11, 12.5), metal),
        cube((3, 11, 3), (13, 12.5, 4), metal),
        cube((3, 11, 12), (13, 12.5, 13), metal),
        cube((3, 11, 4), (4, 12.5, 12), metal),
        cube((12, 11, 4), (13, 12.5, 12), metal),
        # coal bed sits flush with the rim so the embers read from the side
        cube((4, 11, 4), (12, 12, 12), "#ember"),
    ]
    return elements


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def write_models() -> None:
    textures = {
        "particle": "warlockery:block/brazier",
        "metal": "warlockery:block/brazier",
        "ember": "warlockery:block/brazier_ember",
    }
    write_json(BLOCK_MODELS / "brazier.json", {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": textures,
        "elements": bowl_elements(),
    })
    lit_textures = dict(textures)
    lit_textures["flame"] = "warlockery:block/brazier_flame"
    lit = bowl_elements() + [
        flame_plane(45),
        flame_plane(-45),
    ]
    write_json(BLOCK_MODELS / "brazier_lit.json", {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": lit_textures,
        "elements": lit,
    })
    write_json(BLOCKSTATES / "brazier.json", {
        "variants": {
            "lit=false": {"model": "warlockery:block/brazier"},
            "lit=true": {"model": "warlockery:block/brazier_lit"},
        }
    })


def hex_rgb(value: str):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def metal_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16))
    base = hex_rgb("#565a63")
    dark = hex_rgb("#3c3f47")
    light = hex_rgb("#7d828c")
    rivet = hex_rgb("#a4a9b3")
    # Restrained palette: the base iron dominates; sparse hammer marks, one highlight seam, four rivets.
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), dark if (x * 7 + y * 3) % 11 == 0 else base)
    for x in range(0, 16, 2):
        image.putpixel((x, 2), light)
    for x in (1, 5, 9, 13):
        image.putpixel((x, 9), rivet)
    return image


def ember_texture() -> Image.Image:
    image = Image.new("RGBA", (16, 16))
    coal = hex_rgb("#1c1210")
    coal_edge = hex_rgb("#33201a")
    orange = hex_rgb("#f27422")
    yellow = hex_rgb("#ffcf4d")
    # Restrained palette: dark coals dominate with a few glowing seams.
    for y in range(16):
        for x in range(16):
            image.putpixel((x, y), coal_edge if (x * 3 + y * 5) % 13 == 0 else coal)
    glow = [(3, 3), (10, 2), (6, 8), (13, 9), (2, 12), (9, 13)]
    for x, y in glow:
        image.putpixel((x, y), yellow)
        image.putpixel((x + 1, y), orange)
        image.putpixel((x, y + 1), orange)
    return image


def flame_texture() -> Image.Image:
    frames = 4
    image = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    yellow = hex_rgb("#ffd23f")
    orange = hex_rgb("#f2731f")
    red = hex_rgb("#c8321a")
    # three tongues per frame: a tall centre tongue and two shorter side tongues that sway
    tongues = [(7.5, 15, 4.5), (3.0, 9, 2.5), (12.0, 10, 2.5)]
    for frame in range(frames):
        sway = (0.6, -0.6, 0.3, -0.3)[frame]
        for y in range(16):
            row = 15 - y
            for x in range(16):
                pixel = None
                for index, (cx, height, width) in enumerate(tongues):
                    if row > height:
                        continue
                    centre = cx + sway * (1 if index else -1) * (row / height)
                    half = width * (1 - row / (height + 1))
                    distance = abs(x + 0.5 - centre)
                    if distance <= half:
                        core = half * 0.45 if row < height * 0.7 else 0
                        pixel = yellow if distance <= core else (red if row > height * 0.8 else orange)
                if pixel:
                    image.putpixel((x, frame * 16 + y), pixel)
    return image


def write_textures() -> None:
    metal_texture().save(TEXTURES / "brazier.png")
    ember_texture().save(TEXTURES / "brazier_ember.png")
    flame_texture().save(TEXTURES / "brazier_flame.png")
    (TEXTURES / "brazier_flame.png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 3, "interpolate": False}}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    write_models()
    write_textures()
    print("brazier assets written")
