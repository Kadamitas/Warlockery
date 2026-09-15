"""Wolf Head block: a plain wolf's head built from the vanilla wolf entity head geometry.

Copies the vanilla wolf sheet into the block texture tree and writes a block model whose faces
map straight onto the vanilla head, snout and ear islands (scaled x2 to read as a block).
Run from the repository root: python tools/generate_wolf_head_assets.py <path-to-minecraft-client.jar>
"""
from __future__ import annotations

import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BLOCK_MODELS = ROOT / "src/main/resources/assets/warlockery/models/block"
TEXTURES = ROOT / "src/main/resources/assets/warlockery/textures/block"
SHEET_WIDTH, SHEET_HEIGHT = 64, 32


def uv(px0, py0, px1, py1):
    """Convert wolf.png pixel bounds to the 0-16 UV space of a block face."""
    sx, sy = 16 / SHEET_WIDTH, 16 / SHEET_HEIGHT
    return [px0 * sx, py0 * sy, px1 * sx, py1 * sy]


def box(frm, to, island_u, island_v, w, h, d):
    """A block element textured from a standard entity box island (top/bottom row above sides)."""
    u, v = island_u, island_v
    return {
        "from": list(frm),
        "to": list(to),
        "faces": {
            "up": {"texture": "#wolf", "uv": uv(u + d, v, u + d + w, v + d)},
            "down": {"texture": "#wolf", "uv": uv(u + d + w, v, u + 2 * d + w, v + d)},
            "west": {"texture": "#wolf", "uv": uv(u, v + d, u + d, v + d + h)},
            "north": {"texture": "#wolf", "uv": uv(u + d, v + d, u + d + w, v + d + h)},
            "east": {"texture": "#wolf", "uv": uv(u + d + w, v + d, u + 2 * d + w, v + d + h)},
            "south": {"texture": "#wolf", "uv": uv(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)},
        },
    }


def model():
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": "warlockery:block/wolfhead", "wolf": "warlockery:block/wolfhead"},
        "elements": [
            box((2, 2, 5), (14, 14, 13), 0, 0, 6, 6, 4),      # head
            box((5, 3, 0), (11, 9, 5), 0, 10, 3, 3, 4),      # snout
            box((3, 14, 7), (7, 16, 9), 16, 14, 2, 2, 1),    # right ear
            box((9, 14, 7), (13, 16, 9), 16, 14, 2, 2, 1),   # left ear
        ],
    }


def main(jar: Path) -> None:
    with zipfile.ZipFile(jar) as archive:
        data = archive.read("assets/minecraft/textures/entity/wolf/wolf.png")
    sheet = Image.open(io.BytesIO(data)).convert("RGBA")
    assert sheet.size == (SHEET_WIDTH, SHEET_HEIGHT), sheet.size
    sheet.save(TEXTURES / "wolfhead.png")
    (BLOCK_MODELS / "wolfhead.json").write_text(json.dumps(model(), indent=2) + "\n", encoding="utf-8")
    print("wolf head assets written")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
