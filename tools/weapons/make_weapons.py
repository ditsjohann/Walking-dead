"""Generates the 3D in-hand models of the melee weapons.

Run from the repo root:  python3 tools/weapons/make_weapons.py

Each weapon is a few boxes standing along the y axis (handle at the bottom), in block-model units,
textured from one small sheet of materials (textures/item/weapon_materials.png: wood, steel, red
paint, grip tape, ...). In the inventory and on the ground the flat icon is kept; in the hand (first
and third person) it's the 3D model (items/<weapon>.json picks by display context, like the vanilla
spyglass).
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"
CELL = 8           # pixels per material cell
GRID = 4           # cells per row
SHEET = CELL * GRID

MATERIALS = {
    # name: (column, row, base colour, kind)
    "wood": (0, 0, (176, 128, 74), "grain"),
    "wood_dark": (1, 0, (104, 70, 42), "grain"),
    "steel": (2, 0, (150, 154, 160), "brushed"),
    "steel_dark": (3, 0, (88, 92, 98), "brushed"),
    "red": (0, 1, (164, 34, 28), "chipped"),
    "rubber": (1, 1, (34, 34, 36), "plain"),
    "yellow": (2, 1, (226, 186, 40), "plain"),
    "tape": (3, 1, (196, 192, 180), "tape"),
    "blade": (0, 2, (196, 200, 206), "edge"),
    "rust": (1, 2, (128, 70, 40), "plain"),
    "wood_blood": (2, 2, (150, 104, 60), "blood"),
    "chrome": (3, 2, (210, 214, 220), "brushed"),
    "leather": (0, 3, (92, 56, 32), "rivets"),
    "blade_blood": (1, 3, (180, 184, 190), "blood_edge"),
    "blued": (2, 3, (52, 56, 66), "brushed"),
    "polymer": (3, 3, (40, 40, 42), "plain"),
}


def box(f, t, mat, rotation=None):
    return {"from": f, "to": t, "mat": mat, "rotation": rotation}


def bat():
    return [
        box([6.75, -4, 6.75], [9.25, -3, 9.25], "wood_dark"),
        box([7.25, -3, 7.25], [8.75, 5, 8.75], "tape"),
        box([7.1, 5, 7.1], [8.9, 11, 8.9], "wood"),
        box([6.9, 11, 6.9], [9.1, 16, 9.1], "wood"),
        box([6.6, 16, 6.6], [9.4, 22, 9.4], "wood_blood"),
        box([6.5, 22, 6.5], [9.5, 25.5, 9.5], "wood_blood"),
        box([6.8, 25.5, 6.8], [9.2, 26, 9.2], "wood"),
    ]


def knife():
    return [
        box([7.3, 0, 7.5], [8.7, 5, 8.5], "rubber"),
        box([7.2, 5, 7.4], [8.8, 5.6, 8.6], "steel"),
        box([7.1, 5.6, 7.8], [8.8, 10, 8.2], "blade"),
        box([7.1, 10, 7.8], [8.5, 12.5, 8.2], "blade"),
        box([7.1, 12.5, 7.8], [8.0, 14, 8.2], "blade"),
    ]


def machete():
    return [
        box([7.2, 0, 7.4], [8.8, 6, 8.6], "leather"),
        box([6.8, 6, 7.3], [9.2, 6.6, 8.7], "steel_dark"),
        box([6.4, 6.6, 7.8], [9.4, 13, 8.2], "blade"),
        box([6.4, 13, 7.8], [9.4, 18.5, 8.2], "blade_blood"),
        box([6.4, 18.5, 7.8], [8.9, 20.5, 8.2], "blade_blood"),
        box([6.4, 20.5, 7.8], [7.8, 21.5, 8.2], "blade"),
    ]


def crowbar():
    # A straight bar with a flat chisel at the bottom and a hook with a claw at the top.
    return [
        box([7.3, -1.5, 7.6], [8.7, 0, 8.4], "steel_dark"),
        box([7.4, 0, 7.4], [8.6, 16.2, 8.6], "red"),
        box([5.2, 16.2, 7.4], [8.6, 17.4, 8.6], "red"),
        box([4.6, 14.6, 7.4], [5.8, 17.4, 8.6], "red"),
        box([4.6, 13.4, 7.6], [5.8, 14.6, 8.4], "steel_dark"),
    ]


def hammer():
    return [
        box([7.15, 0, 7.15], [8.85, 4, 8.85], "rubber"),
        box([7.3, 4, 7.3], [8.7, 12, 8.7], "wood_dark"),
        box([5, 12, 7], [11, 14.5, 9], "steel"),
        box([11, 12.25, 7.25], [12, 14.25, 8.75], "chrome"),
        box([3.5, 12.6, 7.6], [5, 14, 8.4], "steel_dark"),
    ]


def wrench():
    return [
        box([6.6, -1.5, 7.7], [9.4, 1, 8.3], "chrome"),
        box([7.3, 1, 7.8], [8.7, 12, 8.2], "chrome"),
        box([6.2, 12, 7.7], [9.8, 13.5, 8.3], "chrome"),
        box([6.2, 13.5, 7.7], [7.3, 15.5, 8.3], "chrome"),
        box([8.7, 13.5, 7.7], [9.8, 15.5, 8.3], "chrome"),
    ]


def baton():
    # A police baton (wapenstok): black, with a rubber grip and a steel tip.
    return [
        box([7.1, -1, 7.1], [8.9, 0, 8.9], "steel_dark"),
        box([7.2, 0, 7.2], [8.8, 6, 8.8], "rubber"),
        box([7.35, 6, 7.35], [8.65, 17, 8.65], "steel_dark"),
        box([7.25, 17, 7.25], [8.75, 18.5, 8.75], "steel"),
    ]


def screwdriver():
    return [
        box([7, 0, 7], [9, 5, 9], "yellow"),
        box([6.9, 1, 6.9], [9.1, 1.6, 9.1], "rubber"),
        box([7.5, 5, 7.5], [8.5, 5.6, 8.5], "steel"),
        box([7.7, 5.6, 7.7], [8.3, 12, 8.3], "chrome"),
        box([7.4, 12, 7.85], [8.6, 12.8, 8.15], "steel_dark"),
    ]


# --- Guns: built lying along +y (the barrel pointing ahead), +z up; held by the grip. ---

def pistol():
    return [
        box([7.1, 0, 9], [8.9, 8, 10.6], "blued"),                       # slide
        box([7.2, 0.6, 8], [8.8, 7.6, 9], "polymer"),                    # frame
        box([7.25, 0.4, 4], [8.75, 2.8, 8], "polymer", {"origin": [8, 1.6, 8], "axis": "x", "angle": 22.5}),  # grip
        box([7.6, 2.8, 7.1], [8.4, 4.8, 7.5], "polymer"),                # trigger guard
        box([7.85, 3.0, 7.4], [8.15, 3.5, 8], "steel"),                  # trigger
        box([7.8, 7.2, 10.6], [8.2, 7.7, 11.1], "steel"),                # front sight
        box([7.4, 0.3, 10.6], [8.6, 0.8, 11.1], "steel"),                # rear sight
        box([7.6, 7.9, 9.4], [8.4, 8.2, 10.2], "steel_dark"),            # muzzle
    ]


def shotgun():
    return [
        box([7.2, -8, 4.5], [8.8, -1, 8], "wood"),                       # stock
        box([7.15, -8.6, 4.4], [8.85, -8, 8.1], "rubber"),               # butt pad
        box([7.1, -1, 6.5], [8.9, 4, 9.5], "blued"),                     # receiver
        box([7.4, 4, 8.4], [8.6, 16, 9.4], "blued"),                     # barrel
        box([7.5, 4, 7.2], [8.5, 13, 8.2], "steel_dark"),                # magazine tube
        box([7.05, 6, 6.8], [8.95, 10.5, 8.7], "wood_dark"),             # pump
        box([7.6, 0, 5.6], [8.4, 2, 6.5], "steel_dark"),                 # trigger guard
        box([7.85, 15.2, 9.4], [8.15, 15.6, 9.8], "steel"),              # bead sight
    ]


def rifle():
    return [
        box([7.2, -10, 4.5], [8.8, -1, 8.2], "wood"),                    # stock
        box([7.15, -10.6, 4.4], [8.85, -10, 8.3], "rubber"),             # butt pad
        box([7.2, -1, 7], [8.8, 5, 9.4], "blued"),                       # receiver
        box([7.55, 5, 8], [8.45, 18, 8.9], "blued"),                     # barrel
        box([7.2, 2, 6.6], [8.8, 11.5, 8.2], "wood"),                    # forestock
        box([8.8, 1, 8.4], [10.2, 1.6, 9], "steel"),                     # bolt handle
        box([10.0, 0.9, 8.3], [10.6, 1.7, 9.1], "steel"),                # bolt knob
        box([7.45, -1.5, 10.2], [8.55, 6.5, 11.3], "polymer"),           # scope tube
        box([7.25, 5.5, 10.0], [8.75, 7.2, 11.5], "polymer"),            # scope bell
        box([7.3, -2.3, 10.05], [8.7, -1.3, 11.45], "polymer"),          # eyepiece
        box([7.7, -0.4, 9.4], [8.3, 0.4, 10.2], "steel_dark"),           # mount
        box([7.7, 4.0, 9.4], [8.3, 4.8, 10.2], "steel_dark"),            # mount
        box([7.6, 0, 5.8], [8.4, 2, 7], "steel_dark"),                   # trigger guard
    ]


# Builder, scale, where the hand grips it (y, z).
GUNS = {"pistol": (pistol, 1.0, 1.6, 6.0), "shotgun": (shotgun, 1.05, 0.5, 6.4), "hunting_rifle": (rifle, 1.05, 0.5, 6.6)}


# Builder, scale, where the hand grips it (y), and how it's held in third person (degrees up from
# pointing straight ahead).
WEAPONS = {
    "baseball_bat": (bat, 0.95, 1.0, 70), "kitchen_knife": (knife, 0.85, 2.5, 35), "machete": (machete, 0.95, 3.0, 50),
    "crowbar": (crowbar, 1.0, 3.0, 55), "hammer": (hammer, 1.0, 2.0, 55), "wrench": (wrench, 0.95, 2.0, 50),
    "screwdriver": (screwdriver, 0.85, 2.5, 35), "baton": (baton, 0.95, 2.0, 55),
}
# First person: leaning away up and to the left, from the hand at the bottom right.
FIRST_PERSON_ROTATION = (-18.0, -15.0, 12.0)


def rotate_xyz(v, rx, ry, rz):
    """JOML's rotationXYZ applied to v: Rx(Ry(Rz v)), degrees."""
    x, y, z = v
    a = math.radians(rz)
    x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
    a = math.radians(ry)
    x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    a = math.radians(rx)
    y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    return x, y, z


def display(rotation, scale, grip, offset=(0.0, 0.0, 0.0), grip_z=8.0):
    """A display transform that puts the grip (model y, and z: at the centre of x) at offset from the hand."""
    g = rotate_xyz((0.0, (grip - 8.0) / 16.0 * scale, (grip_z - 8.0) / 16.0 * scale), *rotation)
    translation = [round((offset[i] - g[i]) * 16.0, 3) for i in range(3)]
    scale = round(scale, 3)
    return {"rotation": list(rotation), "translation": translation, "scale": [scale, scale, scale]}


def uv(mat, face, f, t):
    """A face's UV: inside the material's cell, sized to the face (in 16ths of the sheet)."""
    col, row = MATERIALS[mat][:2]
    unit = 16.0 / GRID
    u0, v0 = col * unit, row * unit
    sizes = {"north": (t[0] - f[0], t[1] - f[1]), "south": (t[0] - f[0], t[1] - f[1]),
             "east": (t[2] - f[2], t[1] - f[1]), "west": (t[2] - f[2], t[1] - f[1]),
             "up": (t[0] - f[0], t[2] - f[2]), "down": (t[0] - f[0], t[2] - f[2])}
    w, h = sizes[face]
    w = min(unit, max(0.25, w * unit / 8))
    h = min(unit, max(0.25, h * unit / 8))
    return [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)]


def model(boxes, scale, grip, raised):
    elements = []
    for b in boxes:
        e = {"from": b["from"], "to": b["to"], "faces": {
            face: {"uv": uv(b["mat"], face, b["from"], b["to"]), "texture": "#materials"}
            for face in ("north", "south", "east", "west", "up", "down")}}
        if b["rotation"]:
            e["rotation"] = b["rotation"]
        elements.append(e)
    # Third person the hand's frame has the model's +y pointing ahead and +z up (arm hanging down).
    third = display((raised, 0.0, 0.0), scale * 0.9, grip)
    first = display(FIRST_PERSON_ROTATION, scale * 0.75, grip, (0.0, 0.3, 0.0))
    return {
        "textures": {"materials": "outbreak:item/weapon_materials", "particle": "outbreak:item/weapon_materials"},
        "elements": elements,
        "display": {
            "thirdperson_righthand": third, "thirdperson_lefthand": third,
            "firstperson_righthand": first, "firstperson_lefthand": first,
        },
    }


def gun_model(boxes, scale, grip, grip_z):
    """A gun: barrel along the arm in third person (so it points ahead when aimed, down at rest), and
    pointing into the screen from low on the right in first person."""
    elements = []
    for b in boxes:
        e = {"from": b["from"], "to": b["to"], "faces": {
            face: {"uv": uv(b["mat"], face, b["from"], b["to"]), "texture": "#materials"}
            for face in ("north", "south", "east", "west", "up", "down")}}
        if b["rotation"]:
            e["rotation"] = b["rotation"]
        elements.append(e)
    third = display((-90.0, 0.0, 0.0), scale * 0.85, grip, (0.0, 0.0, -0.06), grip_z)
    first = display((-90.0, 6.0, 0.0), scale * 0.62, grip, (0.0, 0.05, -0.1), grip_z)
    return {
        "textures": {"materials": "outbreak:item/weapon_materials", "particle": "outbreak:item/weapon_materials"},
        "elements": elements,
        "display": {
            "thirdperson_righthand": third, "thirdperson_lefthand": third,
            "firstperson_righthand": first, "firstperson_lefthand": first,
        },
    }


def sheet():
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    rnd = random.Random(7)
    for name, (col, row, base, kind) in MATERIALS.items():
        for y in range(CELL):
            for x in range(CELL):
                d = rnd.randint(-6, 6)
                if kind == "grain":
                    d += 10 if (x + (y // 3)) % 4 == 0 else 0
                    d -= 8 if (x * 3 + y) % 7 == 0 else 0
                elif kind == "brushed":
                    d += 8 if y % 2 else -4
                elif kind == "chipped" and rnd.random() < 0.12:
                    d = 0
                    base_px = (120, 120, 126)
                    img.putpixel((col * CELL + x, row * CELL + y), base_px + (255,))
                    continue
                elif kind == "tape":
                    d += -18 if (x + y) % 4 == 0 else 6
                elif kind == "edge":
                    d += 30 if x == 0 else 0
                elif kind == "rivets" and (x, y) in ((2, 2), (5, 5), (2, 6)):
                    img.putpixel((col * CELL + x, row * CELL + y), (200, 196, 180, 255))
                    continue
                c = tuple(max(0, min(255, v + d)) for v in base)
                if kind in ("blood", "blood_edge") and rnd.random() < 0.35:
                    c = (100 + rnd.randint(-20, 20), 18, 16)
                img.putpixel((col * CELL + x, row * CELL + y), c + (255,))
    return img


def main():
    tex = ROOT / "textures/item/weapon_materials.png"
    sheet().save(tex)
    print("wrote", tex.relative_to(ROOT.parents[3]))
    models = {name: model(build(), scale, grip, raised) for name, (build, scale, grip, raised) in WEAPONS.items()}
    models.update({name: gun_model(build(), scale, grip, grip_z) for name, (build, scale, grip, grip_z) in GUNS.items()})
    for name, data in models.items():
        out = ROOT / "models/item" / f"{name}_in_hand.json"
        out.write_text(json.dumps(data, indent="\t") + "\n")
        definition = {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": f"outbreak:item/{name}"}}],
            "fallback": {"type": "minecraft:model", "model": f"outbreak:item/{name}_in_hand"}}}
        (ROOT / "items" / f"{name}.json").write_text(json.dumps(definition, indent="\t") + "\n")
        print("wrote", out.relative_to(ROOT.parents[3]))


if __name__ == "__main__":
    main()
