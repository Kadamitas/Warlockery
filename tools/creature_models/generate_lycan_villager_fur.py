#!/usr/bin/env python3
"""Deterministic painter for the Lycan Villager atlas (128x64: vanilla villager UV layout on the left half, werewolf head islands on the right).

The rig is the vanilla villager body carrying a werewolf head (skull, muzzle, jaw, brow, ears), so this atlas follows the vanilla
``villager.png`` island layout exactly. Every island the LycanVillagerModel samples is painted
opaque except the hat rim plane (texOffs 30,47), which stays transparent: vanilla profession
textures own the brim through the native clothing layer, and an opaque plane there would slice
straight through the face. Exposed villager skin (face, nose, hands, feet) becomes grey-brown
wolf fur; the robe islands keep a neutral earthy base that vanilla biome/profession clothing
renders over.

Usage: python tools/creature_models/generate_lycan_villager_fur.py [repository_root]
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

WIDTH = 128
HEIGHT = 64

# Fur ramp (dark -> light), grey-brown wolf.
FUR_SHADOW = (46, 42, 40)
FUR_DARK = (66, 61, 57)
FUR_BASE = (96, 90, 84)
FUR_LIGHT = (122, 115, 106)
FUR_TIP = (146, 138, 126)
MUZZLE_DARK = (58, 53, 50)
MUZZLE_BASE = (78, 72, 67)
NOSE_LEATHER = (30, 27, 26)
NOSE_SHINE = (64, 58, 56)
EYE_AMBER = (222, 166, 52)
EYE_AMBER_DEEP = (176, 118, 30)
EYE_PUPIL = (36, 26, 14)
EAR_PINK = (168, 112, 108)
EAR_PINK_DEEP = (128, 80, 80)
CLAW = (198, 188, 168)

# Robe ramp: neutral earthy wool, similar to the vanilla villager base robe.
ROBE_SHADOW = (74, 54, 38)
ROBE_DARK = (98, 72, 50)
ROBE_BASE = (128, 96, 66)
ROBE_LIGHT = (150, 118, 84)
ROBE_STITCH = (108, 84, 58)
TRIM_GREEN = (82, 96, 60)
TRIM_GREEN_DEEP = (60, 72, 44)
BELT = (58, 42, 30)
BELT_BUCKLE = (150, 128, 78)


def hash_noise(x: int, y: int, salt: int = 0) -> int:
    """Small deterministic integer hash in [0, 255]."""
    value = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    value = ((value ^ (value >> 13)) * 1274126177) & 0xFFFFFFFF
    return (value ^ (value >> 16)) & 0xFF


class Atlas:
    def __init__(self) -> None:
        self.image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
        self.pixels = self.image.load()

    def put(self, x: int, y: int, color: tuple[int, int, int]) -> None:
        if 0 <= x < WIDTH and 0 <= y < HEIGHT:
            self.pixels[x, y] = (color[0], color[1], color[2], 255)

    def rect(self, x: int, y: int, w: int, h: int, color: tuple[int, int, int]) -> None:
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, color)

    def fur(self, x: int, y: int, w: int, h: int, salt: int, ramp=None, tuft_height: int = 3) -> None:
        """Fur as staggered vertical strands: a dominant base coat with 1-pixel-wide light and dark
        strands a few pixels tall, offset per column so the coat reads as hair rather than a grid."""
        ramp = ramp or (FUR_DARK, FUR_BASE, FUR_LIGHT, FUR_TIP)
        dark, base, light, tip = ramp[0], ramp[1], ramp[2], ramp[-1]
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                column = xx - x
                stagger = (column * 2) % tuft_height
                strand = (yy - y + stagger) // tuft_height
                noise = hash_noise(column, strand, salt)
                if noise < 40:
                    color = dark
                elif noise < 150:
                    color = base
                elif noise < 215:
                    color = light
                else:
                    color = tip
                # Root of each strand sits a step darker for depth.
                if (yy - y + stagger) % tuft_height == tuft_height - 1 and color is not dark \
                        and hash_noise(xx, yy, salt + 7) < 96:
                    color = base if color is not base else dark
                self.put(xx, yy, color)

    def cloth(self, x: int, y: int, w: int, h: int, salt: int) -> None:
        """Woven robe: broad flat base with horizontal weave bands and soft shading clusters."""
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                band = (yy - y) % 4
                if band == 3:
                    color = ROBE_DARK
                elif band == 1 and hash_noise((xx - x) // 3, (yy - y) // 4, salt) < 90:
                    color = ROBE_LIGHT
                else:
                    color = ROBE_BASE
                if hash_noise((xx - x) // 2, (yy - y) // 2, salt + 3) < 28:
                    color = ROBE_STITCH
                self.put(xx, yy, color)


def paint_head(atlas: Atlas) -> None:
    # Werewolf skull 8x8x8 at texOffs(0,0): top (8,0) 8x8, bottom (16,0) 8x8,
    # right (0,8) 8x8, front (8,8) 8x8, left (16,8) 8x8, back (24,8) 8x8. Rows 16..17 of the old
    # 8x10 villager head island are painted too so the vanilla footprint stays fully opaque.
    atlas.fur(8, 0, 8, 8, salt=11)          # crown
    atlas.fur(16, 0, 8, 8, salt=12, ramp=(FUR_SHADOW, FUR_DARK, FUR_DARK, FUR_BASE, FUR_BASE))  # underside
    atlas.fur(0, 8, 8, 10, salt=13)         # right cheek
    atlas.fur(16, 8, 8, 10, salt=14)        # left cheek
    atlas.fur(24, 8, 8, 10, salt=15)        # back of head
    atlas.fur(24, 0, 8, 8, salt=17)         # old nose pocket, kept furred
    atlas.fur(0, 0, 8, 8, salt=18)          # unused pocket, kept furred
    # Cheek ruffs sweeping back from the jaw on the side faces.
    for xx in range(0, 8):
        atlas.put(xx, 14 + (xx % 2), FUR_SHADOW if xx % 3 else FUR_DARK)
        atlas.put(16 + xx, 14 + ((xx + 1) % 2), FUR_SHADOW if xx % 3 else FUR_DARK)
    # Face.
    atlas.fur(8, 8, 8, 10, salt=16)
    # Brow ridge shadow under the wedge.
    for xx in range(8, 16):
        atlas.put(xx, 10, FUR_DARK)
    # Amber eyes with slit pupils, set wide either side of the muzzle.
    for ex in (9, 13):
        atlas.put(ex, 11, EYE_AMBER)
        atlas.put(ex + 1, 11, EYE_AMBER)
        atlas.put(ex, 12, EYE_AMBER_DEEP)
        atlas.put(ex + 1, 12, EYE_AMBER_DEEP)
    atlas.put(10, 11, EYE_PUPIL)
    atlas.put(13, 11, EYE_PUPIL)
    # Darker fur where the muzzle box meets the face.
    for yy in range(13, 16):
        for xx in range(10, 14):
            atlas.put(xx, yy, MUZZLE_DARK if (xx + yy) % 3 == 0 else MUZZLE_BASE)


def paint_muzzle(atlas: Atlas) -> None:
    # Muzzle box 5x3x3 at texOffs(64,0): top (67,0) 5x3, bottom (72,0) 5x3,
    # right (64,3) 3x3, front (67,3) 5x3, left (72,3) 3x3, back (75,3) 5x3.
    atlas.rect(64, 0, 16, 6, MUZZLE_BASE)
    atlas.fur(67, 0, 5, 3, salt=71, ramp=(MUZZLE_DARK, MUZZLE_BASE, MUZZLE_BASE, FUR_BASE, FUR_BASE))
    atlas.rect(72, 0, 5, 3, MUZZLE_DARK)
    atlas.fur(64, 3, 3, 3, salt=72, ramp=(MUZZLE_DARK, MUZZLE_BASE, MUZZLE_BASE, FUR_BASE, FUR_BASE))
    atlas.fur(72, 3, 3, 3, salt=73, ramp=(MUZZLE_DARK, MUZZLE_BASE, MUZZLE_BASE, FUR_BASE, FUR_BASE))
    atlas.fur(75, 3, 5, 3, salt=74, ramp=(MUZZLE_DARK, MUZZLE_BASE, MUZZLE_BASE, FUR_BASE, FUR_BASE))
    # Front: black nose leather across the top row, dark lip line below.
    atlas.rect(67, 3, 5, 3, MUZZLE_BASE)
    atlas.rect(68, 3, 3, 1, NOSE_LEATHER)
    atlas.put(68, 3, NOSE_SHINE)
    atlas.rect(67, 5, 5, 1, MUZZLE_DARK)
    atlas.put(69, 4, NOSE_LEATHER)
    # Lower jaw box 4x1.5x3 at texOffs(64,8): island 14x5 -> (64..78, 8..13).
    atlas.rect(64, 8, 14, 5, MUZZLE_DARK)
    atlas.fur(67, 8, 4, 3, salt=75, ramp=(MUZZLE_DARK, MUZZLE_BASE, MUZZLE_BASE, FUR_BASE, FUR_BASE))
    atlas.rect(71, 8, 4, 3, FUR_SHADOW)
    atlas.rect(67, 11, 4, 2, MUZZLE_BASE)
    atlas.put(68, 11, MUZZLE_DARK)
    atlas.put(69, 11, MUZZLE_DARK)
    # Brow wedge box 6x2x2 at texOffs(64,16): island 16x4 -> (64..80, 16..20).
    atlas.rect(64, 16, 16, 4, FUR_DARK)
    atlas.fur(66, 16, 6, 2, salt=76)
    atlas.fur(66, 18, 6, 2, salt=77, ramp=(FUR_SHADOW, FUR_DARK, FUR_DARK, FUR_BASE, FUR_BASE))
    atlas.rect(72, 18, 2, 2, FUR_SHADOW)
    atlas.fur(74, 18, 6, 2, salt=78, ramp=(FUR_SHADOW, FUR_DARK, FUR_DARK, FUR_BASE, FUR_BASE))


def paint_hat_shell(atlas: Atlas) -> None:
    # Hat box shares the head layout at texOffs(32,0); painted as a slightly shaggier fur coat
    # so the base render reads as a thick wolf pelt. Vanilla type/profession hats render over it.
    atlas.fur(40, 0, 8, 8, salt=21, tuft_height=3)
    atlas.fur(48, 0, 8, 8, salt=22, ramp=(FUR_SHADOW, FUR_DARK, FUR_DARK, FUR_BASE, FUR_BASE), tuft_height=3)
    atlas.fur(32, 8, 8, 10, salt=23, tuft_height=3)
    atlas.fur(40, 8, 8, 10, salt=24, tuft_height=3)
    atlas.fur(48, 8, 8, 10, salt=25, tuft_height=3)
    atlas.fur(56, 8, 8, 10, salt=26, tuft_height=3)
    # Keep the face opening honest: the front hat face gets the same brow/eye/muzzle marks so the
    # shell never hides the amber eyes behind flat fur.
    for xx in range(41, 47):
        atlas.put(xx, 11, FUR_DARK)
    for ex in (41, 45):
        atlas.put(ex, 12, EYE_AMBER)
        atlas.put(ex + 1, 12, EYE_AMBER)
        atlas.put(ex, 13, EYE_AMBER_DEEP)
        atlas.put(ex + 1, 13, EYE_AMBER_DEEP)
    atlas.put(42, 12, EYE_PUPIL)
    atlas.put(45, 12, EYE_PUPIL)
    for yy in range(14, 18):
        for xx in range(42, 46):
            atlas.put(xx, yy, MUZZLE_DARK if (xx + yy) % 3 == 0 else MUZZLE_BASE)


def paint_ears(atlas: Atlas) -> None:
    # Ear box 2x4x1: top (u+1,v) 2x1, bottom (u+3,v) 2x1, right (u,v+1) 1x4, front (u+1,v+1) 2x4,
    # left (u+3,v+1) 1x4, back (u+4,v+1) 2x4. Islands at (28,38) and (34,38).
    for u, mirror in ((28, False), (34, True)):
        atlas.rect(u, 38, 6, 5, FUR_DARK)
        atlas.rect(u + 1, 38, 2, 1, FUR_BASE)          # top tip
        atlas.rect(u + 3, 38, 2, 1, FUR_SHADOW)        # underside
        atlas.rect(u + 4, 39, 2, 4, FUR_BASE)          # back of ear: outer fur
        atlas.put(u + 4, 40, FUR_LIGHT)
        atlas.put(u + 5, 42, FUR_DARK)
        # Front face: pink inner ear with a dark fur rim.
        inner_x = u + 2 if mirror else u + 1
        rim_x = u + 1 if mirror else u + 2
        atlas.put(inner_x, 39, EAR_PINK_DEEP)
        atlas.put(inner_x, 40, EAR_PINK)
        atlas.put(inner_x, 41, EAR_PINK)
        atlas.put(inner_x, 42, EAR_PINK_DEEP)
        atlas.put(rim_x, 39, FUR_DARK)
        atlas.put(rim_x, 40, FUR_BASE)
        atlas.put(rim_x, 41, FUR_BASE)
        atlas.put(rim_x, 42, FUR_DARK)
        atlas.put(u, 40, FUR_BASE)                     # right edge highlight
        atlas.put(u + 3, 40, FUR_BASE)                 # left edge highlight


def paint_body(atlas: Atlas) -> None:
    # Body box 8x12x6 at texOffs(16,20): top (22,20) 8x6, bottom (30,20) 8x6,
    # right (16,26) 6x12, front (22,26) 8x12, left (30,26) 6x12, back (36,26) 8x12.
    atlas.cloth(16, 20, 28, 18, salt=31)
    # Shoulder yoke of fur peeking above the collar on the top face.
    atlas.fur(22, 20, 8, 6, salt=32, ramp=(FUR_DARK, FUR_BASE, FUR_BASE, FUR_LIGHT, FUR_LIGHT))
    atlas.rect(30, 20, 8, 6, ROBE_SHADOW)
    # Front placket and green collar trim (concept palette accent), belt across the waist.
    atlas.rect(22, 26, 8, 2, TRIM_GREEN)
    atlas.rect(23, 26, 6, 1, TRIM_GREEN_DEEP)
    atlas.rect(25, 28, 2, 6, ROBE_DARK)
    atlas.put(25, 29, TRIM_GREEN_DEEP)
    atlas.put(26, 31, TRIM_GREEN_DEEP)
    atlas.rect(16, 34, 28, 1, BELT)
    atlas.rect(25, 34, 2, 1, BELT_BUCKLE)
    atlas.rect(16, 35, 28, 1, ROBE_SHADOW)
    # Back seam.
    atlas.rect(40, 27, 1, 7, ROBE_DARK)


def paint_jacket(atlas: Atlas) -> None:
    # Jacket box 8x20x6 (0.5 inflated) at texOffs(0,38): top (6,38) 8x6, bottom (14,38) 8x6,
    # right (0,44) 6x20, front (6,44) 8x20, left (14,44) 6x20, back (20,44) 8x20.
    atlas.cloth(0, 38, 28, 26, salt=41)
    atlas.rect(6, 38, 8, 6, ROBE_DARK)
    atlas.fur(7, 39, 6, 4, salt=42, ramp=(FUR_DARK, FUR_BASE, FUR_BASE, FUR_LIGHT, FUR_LIGHT))
    atlas.rect(14, 38, 8, 6, ROBE_SHADOW)
    # Collar trim, belt and a long front placket so the base jacket reads as a plain robe.
    atlas.rect(6, 44, 8, 2, TRIM_GREEN)
    atlas.rect(7, 44, 6, 1, TRIM_GREEN_DEEP)
    atlas.rect(9, 46, 2, 10, ROBE_DARK)
    atlas.put(9, 48, TRIM_GREEN_DEEP)
    atlas.put(10, 51, TRIM_GREEN_DEEP)
    atlas.rect(0, 52, 28, 1, BELT)
    atlas.rect(9, 52, 2, 1, BELT_BUCKLE)
    atlas.rect(0, 53, 28, 1, ROBE_SHADOW)
    # Hem shadow.
    atlas.rect(0, 62, 28, 2, ROBE_SHADOW)
    for xx in range(1, 28, 3):
        atlas.put(xx, 62, ROBE_DARK)
    atlas.rect(24, 45, 1, 7, ROBE_DARK)


def paint_arms(atlas: Atlas) -> None:
    # Arm box 4x8x4 at texOffs(44,22): top (48,22) 4x4, bottom (52,22) 4x4,
    # right (44,26) 4x8, front (48,26) 4x8, left (52,26) 4x8, back (56,26) 4x8.
    atlas.cloth(44, 22, 16, 12, salt=51)
    atlas.rect(48, 22, 4, 4, ROBE_DARK)
    # Sleeve cuff then furred hands on the lower half of every side face.
    atlas.rect(44, 29, 16, 1, ROBE_SHADOW)
    atlas.fur(44, 30, 16, 4, salt=52, ramp=(FUR_DARK, FUR_BASE, FUR_BASE, FUR_LIGHT, FUR_TIP))
    # Bottom face: paw pads / claws.
    atlas.fur(52, 22, 4, 4, salt=53, ramp=(FUR_SHADOW, FUR_DARK, FUR_DARK, FUR_BASE, FUR_BASE))
    atlas.put(52, 23, CLAW)
    atlas.put(54, 23, CLAW)
    atlas.put(53, 25, CLAW)
    # Claw tips on the front face of the hands.
    atlas.put(48, 33, CLAW)
    atlas.put(50, 33, CLAW)
    # Folded forearm block 8x4x4 at texOffs(40,38): top (44,38) 8x4, bottom (52,38) 8x4,
    # right (40,42) 4x4, front (44,42) 8x4, left (52,42) 4x4, back (56,42) 8x4.
    atlas.cloth(40, 38, 24, 8, salt=54)
    atlas.rect(44, 38, 8, 4, ROBE_LIGHT)
    atlas.rect(44, 39, 8, 1, ROBE_BASE)
    atlas.rect(44, 41, 8, 1, ROBE_DARK)
    atlas.rect(52, 38, 8, 4, ROBE_SHADOW)
    # Fur knuckles wrapping the front of the folded arms.
    atlas.fur(44, 42, 8, 2, salt=55, ramp=(FUR_DARK, FUR_BASE, FUR_BASE, FUR_LIGHT, FUR_LIGHT))
    atlas.put(45, 42, CLAW)
    atlas.put(50, 42, CLAW)


def paint_legs(atlas: Atlas) -> None:
    # Leg box 4x12x4 at texOffs(0,22): top (4,22) 4x4, bottom (8,22) 4x4,
    # right (0,26) 4x12, front (4,26) 4x12, left (8,26) 4x12, back (12,26) 4x12.
    atlas.cloth(0, 22, 16, 16, salt=61)
    atlas.rect(4, 22, 4, 4, ROBE_DARK)
    # Robe hem, then furred feet on the last rows and the sole.
    atlas.rect(0, 34, 16, 1, ROBE_SHADOW)
    atlas.fur(0, 35, 16, 3, salt=62, ramp=(FUR_DARK, FUR_BASE, FUR_BASE, FUR_LIGHT, FUR_TIP))
    atlas.fur(8, 22, 4, 4, salt=63, ramp=(FUR_SHADOW, FUR_DARK, FUR_DARK, FUR_BASE, FUR_BASE))
    atlas.put(4, 37, CLAW)
    atlas.put(6, 37, CLAW)
    atlas.put(8, 23, CLAW)
    atlas.put(10, 23, CLAW)


def generate() -> Image.Image:
    atlas = Atlas()
    paint_head(atlas)
    paint_muzzle(atlas)
    paint_hat_shell(atlas)
    paint_ears(atlas)
    paint_body(atlas)
    paint_jacket(atlas)
    paint_arms(atlas)
    paint_legs(atlas)
    # Hat rim island (30..64, 47..64) intentionally stays transparent; see module docstring.
    return atlas.image


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parents[2]
    target = root / "src" / "main" / "resources" / "assets" / "warlockery" / "textures" / "entity" / "lycan_villager.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    generate().save(target, format="PNG", optimize=False)
    print(f"Wrote {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
