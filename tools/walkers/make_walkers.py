"""Generates the walker model (with elbows and knees) and its skins.

Run from the repo root:  python3 tools/walkers/make_walkers.py

The model is a tree of boxes in pixels, y up from the feet, x to the walker's left, z forward (the
JSON format VehicleModel/BoxModels read; for a humanoid, Minecraft's model front is -z, the face is
on the box's north side). The skins are painted per body part from an outfit, a skin tone (dead,
in different stages), a hair style and some gore, so every walker looks a bit different.
Writes assets/outbreak/models/walker.json and textures/entity/walker/walker_<n>.png.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"
COUNT = 24
TEX_W, TEX_H = 64, 64


class Box:
    def __init__(self, origin, size, inflate=0.0):
        self.origin = origin
        self.size = size
        self.inflate = inflate
        self.uv = None


class Part:
    def __init__(self, name, pivot, boxes=(), children=()):
        self.name = name
        self.pivot = pivot
        self.boxes = list(boxes)
        self.children = list(children)

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()

    def to_json(self):
        return {"name": self.name, "pivot": list(self.pivot), "rotation": [0, 0, 0],
                "boxes": [{"origin": list(b.origin), "size": list(b.size), "uv": list(b.uv), "inflate": b.inflate} for b in self.boxes],
                "children": [c.to_json() for c in self.children]}


def limb(name, pivot, lower_name, x0):
    """An upper limb hanging from pivot with a lower limb from its middle (elbow or knee)."""
    return Part(name, pivot, [Box((x0, -6, -2), (4, 6, 4))],
                [Part(lower_name, (0, -6, 0), [Box((x0, -6, -2), (4, 6, 4))])])


def model():
    # Absolute heights: feet 0, knees 6, hips 12, belly 15, shoulders 24, head top 32.
    head = Part("head", (0, 9, 0), [Box((-4, 0, -4), (8, 8, 8))],
                [Part("hat", (0, 0, 0), [Box((-4, 0, -4), (8, 8, 8), inflate=0.5)])])
    right_arm = Part("right_arm", (-5, 7, 0), [Box((-3, -4, -2), (4, 6, 4))],
                     [Part("right_forearm", (-1, -4, 0), [Box((-2, -6, -2), (4, 6, 4))])])
    left_arm = Part("left_arm", (5, 7, 0), [Box((-1, -4, -2), (4, 6, 4))],
                    [Part("left_forearm", (1, -4, 0), [Box((-2, -6, -2), (4, 6, 4))])])
    chest = Part("chest", (0, 3, 0), [Box((-4, 0, -2), (8, 9, 4))], [head, right_arm, left_arm])
    right_leg = limb("right_leg", (-2, 0, 0), "right_shin", -2)
    left_leg = limb("left_leg", (2, 0, 0), "left_shin", -2)
    return [Part("waist", (0, 12, 0), [Box((-4, 0, -2), (8, 3, 4))], [chest, right_leg, left_leg])]


def footprint(b):
    w, h, d = b.size
    return 2 * (w + d), d + h


def pack(parts):
    boxes = sorted((b for p in parts for q in p.walk() for b in q.boxes), key=lambda b: -footprint(b)[1])
    x = y = shelf = 0
    for b in boxes:
        fw, fh = footprint(b)
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        b.uv = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= TEX_H, y + shelf


# --- Looks ---------------------------------------------------------------------------------------

SKIN = [  # the dead, from fresh to far gone
    (150, 156, 132), (128, 140, 112), (162, 150, 120), (118, 120, 104), (136, 122, 116), (104, 112, 92),
    (110, 96, 80), (92, 86, 74),
]
HAIR = [None, (40, 30, 22), (30, 26, 24), (150, 120, 70), (110, 70, 40), (140, 136, 130), (120, 50, 28), (70, 54, 40)]


def outfit_list():
    jeans = (52, 70, 104)
    dark = (40, 40, 46)
    return [
        # name, shirt, sleeves (0 none, 1 short, 2 long), legs (pants colour or None for skin), shoes, extra
        ("casual_red", (150, 40, 36), 1, jeans, (220, 220, 214), None),
        ("casual_teal", (40, 120, 120), 1, jeans, (60, 60, 64), None),
        ("office", (220, 222, 226), 2, dark, (30, 24, 20), "tie"),
        ("police", (34, 40, 66), 2, (30, 34, 52), (20, 20, 22), "police"),
        ("nurse", (110, 170, 168), 1, (110, 170, 168), (230, 230, 230), None),
        ("doctor", (232, 232, 232), 2, (70, 80, 100), (40, 34, 30), "coat"),
        ("construction", (90, 90, 96), 2, (60, 60, 66), (90, 64, 40), "hivis"),
        ("student", (82, 92, 70), 2, jeans, (200, 200, 196), "hoodie"),
        ("fc_groningen", (40, 120, 60), 1, jeans, (240, 240, 240), "stripes"),
        ("jogger", (220, 120, 40), 0, None, (230, 70, 60), "shorts"),
        ("elderly", (120, 100, 140), 2, (110, 104, 96), (60, 44, 32), "cardigan"),
        ("soldier", (88, 98, 64), 2, (88, 98, 64), (36, 30, 26), "camo"),
        ("cyclist", (230, 200, 40), 2, jeans, (60, 60, 64), "raincoat"),
        ("farmer", (170, 60, 50), 2, (50, 70, 120), (80, 56, 36), "overalls"),
        ("dress", (120, 40, 80), 1, None, (40, 30, 30), "dress"),
        ("chef", (236, 236, 232), 2, (36, 36, 40), (30, 30, 30), "apron"),
    ]


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)


class Look:
    def __init__(self, n):
        rnd = random.Random(f"walker-{n}")
        outfits = outfit_list()
        self.outfit = outfits[n % len(outfits)]
        self.skin = SKIN[(n * 5 + n // len(outfits)) % len(SKIN)]
        self.hair = HAIR[(n * 3 + 1) % len(HAIR)] if self.outfit[5] not in ("police", "soldier") else HAIR[1]
        self.long_hair = rnd.random() < 0.3 and self.hair is not None
        self.gore = 0.6 + rnd.random() * 0.8
        self.rnd = rnd
        self.bites = [(rnd.choice(["chest", "right_arm", "left_arm", "right_forearm", "left_forearm", "head", "right_leg", "waist"]),
                       rnd.random(), rnd.random()) for _ in range(rnd.randint(1, 3))]


def paint(img, look, part, b):
    w, h, d = b.size
    u, v = b.uv
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, (x0, y0, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = color(look, part, face, x, y, fw, fh)
                if c is not None:
                    img.putpixel((x0 + x, y0 + y), c)


def skin(look, x, y, salt=0):
    r = look.rnd
    c = shade(look.skin, r.randint(-8, 8))
    if r.random() < 0.06:  # rot and bruises
        c = shade((96, 70, 84), r.randint(-10, 10))
    if r.random() < 0.03:
        c = shade((70, 80, 50), r.randint(-6, 6))
    return c


def cloth(look, base, x, y):
    r = look.rnd
    c = shade(base, r.randint(-6, 6))
    if r.random() < 0.07 * look.gore:  # dirt and old blood
        c = shade((90, 28, 24), r.randint(-20, 10)) if r.random() < 0.6 else shade((70, 60, 46), r.randint(-10, 10))
    return c


def color(look, part, face, x, y, fw, fh):
    name, shirt, sleeves, pants, shoes, extra = look.outfit
    r = look.rnd
    if part == "hat":
        return hair_pixel(look, face, x, y, fw, fh)
    if part == "head":
        if face == "front":
            # Eyes: pale and dead, in dark sockets. Mouth: open, dark, teeth.
            if y in (3, 4) and x in (1, 2, 5, 6):
                return (40, 30, 30, 255) if y == 3 else ((214, 214, 190, 255) if x in (2, 5) else (60, 40, 40, 255))
            if y == 6 and 2 <= x <= 5:
                return (30, 14, 14, 255)
            if y == 7 and 2 <= x <= 5:
                return (200, 190, 160, 255) if x % 2 else (40, 20, 20, 255)
            if y == 5 and x in (3, 4):
                return shade(look.skin, -30)
        c = skin(look, x, y)
        return blood(look, part, face, x, y, fw, fh, c)
    if part in ("right_arm", "left_arm", "right_forearm", "left_forearm"):
        lower = "forearm" in part
        covered = sleeves == 2 or (sleeves == 1 and not lower and y < fh - 2) or (extra == "coat")
        if face == "bottom" and lower:
            covered = False
        if extra in ("dress",) and not lower and sleeves == 1 and y < 2:
            covered = True
        if covered and not (lower and y >= fh - 2):  # hands stay bare
            base = jacket(look, part, face, x, y, fw, fh)
            return blood(look, part, face, x, y, fw, fh, cloth(look, base, x, y))
        return blood(look, part, face, x, y, fw, fh, skin(look, x, y))
    if part in ("chest", "waist"):
        base = jacket(look, part, face, x, y, fw, fh)
        if part == "waist":
            if extra in ("dress",):
                base = shirt
            elif extra == "overalls":
                base = pants
            elif pants is not None and extra not in ("coat",):
                base = pants if y >= 1 or face in ("bottom",) else (30, 26, 22)  # belt line
            elif extra == "shorts":
                base = (40, 40, 60)
        return blood(look, part, face, x, y, fw, fh, cloth(look, base, x, y))
    # Legs.
    lower = "shin" in part
    if lower and (y >= fh - 2 or face == "bottom"):
        return cloth(look, shoes, x, y) if face != "bottom" else shade(shoes, -40)
    if extra == "dress" and not lower:
        return cloth(look, shirt, x, y) if y < 4 else blood(look, part, face, x, y, fw, fh, skin(look, x, y))
    if extra == "shorts" and not lower:
        return cloth(look, (40, 40, 60), x, y) if y < 3 else blood(look, part, face, x, y, fw, fh, skin(look, x, y))
    if pants is None:
        return blood(look, part, face, x, y, fw, fh, skin(look, x, y))
    base = pants
    if extra == "camo" and r.random() < 0.3:
        base = r.choice([(60, 70, 44), (110, 104, 70), (50, 46, 36)])
    if extra == "police" and face in ("left", "right") and x == fw // 2:
        base = (200, 180, 40)
    return blood(look, part, face, x, y, fw, fh, cloth(look, base, x, y))


def jacket(look, part, face, x, y, fw, fh):
    """The upper body's clothes, with each outfit's details."""
    name, shirt, sleeves, pants, shoes, extra = look.outfit
    r = look.rnd
    base = shirt
    chest = part == "chest"
    if extra == "tie" and chest and face == "front" and x in (3, 4) and y >= 1:
        return (120, 20, 30, 255)
    if extra == "police":
        if chest and face == "front" and y == 4:
            return (230, 200, 40, 255) if x % 2 else (30, 60, 160, 255)  # Dutch police stripes
        if chest and face == "front" and (x, y) in ((1, 1), (2, 1)):
            return (210, 200, 120, 255)  # badge
    if extra == "hivis" and chest:
        if face in ("front", "back"):
            base = (240, 120, 30)
            if y in (5, 6):
                base = (220, 220, 210)
        elif face in ("left", "right"):
            base = (240, 120, 30)
    if extra == "stripes" and chest and face in ("front", "back"):
        base = (40, 120, 60) if (x // 2) % 2 == 0 else (236, 236, 236)
    if extra == "camo" and r.random() < 0.3:
        base = r.choice([(60, 70, 44), (110, 104, 70), (50, 46, 36)])
    if extra == "coat" and chest and face == "front" and x in (3, 4):
        return (90, 140, 160, 255)  # scrubs under the coat
    if extra == "hoodie" and chest and face == "front" and 2 <= x <= 5 and 4 <= y <= 6:
        return shade(shirt, -18)  # pocket
    if extra == "cardigan" and chest and face == "front" and x in (3, 4):
        return (220, 210, 190, 255) if y % 2 else shade(shirt, -20)
    if extra == "overalls" and chest and face in ("front", "back") and y >= 3 and 1 <= x <= 6:
        base = (50, 70, 120)
    if extra == "overalls" and chest and face in ("front", "back") and y < 3 and x in (2, 5):
        base = (50, 70, 120)
    if extra == "apron" and face == "front" and (chest and y >= 3 or part == "waist"):
        base = (30, 30, 34)
    if extra == "raincoat" and chest and face == "front" and x == 4:
        return (150, 130, 30, 255)
    if extra == "dress" and part == "waist":
        base = shirt
    return base


def hair_pixel(look, face, x, y, fw, fh):
    """The hat layer: hair (patchy), a helmet or a cap. Transparent elsewhere."""
    name, shirt, sleeves, pants, shoes, extra = look.outfit
    r = look.rnd
    if extra == "hivis":
        if face == "top" or y <= 2:
            return shade((236, 200, 40), r.randint(-6, 6))
        return None
    if extra == "camo":
        if face == "top" or y <= 2:
            return shade((70, 80, 52), r.randint(-8, 8))
        return None
    if extra == "police" and (face == "top" or y <= 1):
        return shade((24, 28, 50), r.randint(-4, 4)) if not (face == "front" and y == 1) else (20, 20, 22, 255)
    if look.hair is None:
        return None
    if r.random() < 0.18:  # patches torn out
        return None
    c = shade(look.hair, r.randint(-10, 10))
    if face == "top":
        return c
    if face == "front":
        return c if y <= 1 else None
    if face == "back":
        return c if y <= (7 if look.long_hair else 5) else None
    return c if y <= (6 if look.long_hair else 3) else None


def blood(look, part, face, x, y, fw, fh, c):
    """Bites and gashes, darker blood around them."""
    for where, bx, by in look.bites:
        if where != part or face not in ("front", "left", "right"):
            continue
        cx, cy = bx * fw, by * fh
        dist = math.hypot(x - cx, (y - cy) * 1.3)
        if dist < 1.2:
            return (58, 10, 10, 255)
        if dist < 2.4:
            return (120, 18, 16, 255)
        if dist < 3.4 and look.rnd.random() < 0.5:
            return (96, 30, 26, 255)
    if part == "head" and face == "front" and y >= 6 and look.rnd.random() < 0.25 * look.gore:
        return (110, 20, 18, 255)  # blood down the chin
    if part == "chest" and face == "front" and y <= 3 and look.rnd.random() < 0.12 * look.gore:
        return (110, 20, 18, 255)
    return c


def main():
    parts = model()
    pack(parts)
    out = ROOT / "models" / "walker.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"texture": [TEX_W, TEX_H], "parts": [p.to_json() for p in parts]}, indent="\t") + "\n")
    print("wrote", out.relative_to(ROOT.parents[3]))
    for n in range(COUNT):
        look = Look(n)
        img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        for p in parts:
            for q in p.walk():
                for b in q.boxes:
                    paint(img, look, q.name, b)
        tex = ROOT / "textures/entity/walker" / f"walker_{n}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        img.save(tex)
    print(f"wrote {COUNT} skins: " + ", ".join(Look(n).outfit[0] for n in range(COUNT)))


if __name__ == "__main__":
    main()
