"""Generates the blood: puddle textures, block models and the block state file.

Run from the repo root:  python3 tools/gore/make_gore.py

Blood on walls (outbreak:wall_blood) is a thin layer against a wall: a splash with runs dripping
down, in two sizes and the same four ages. A puddle (outbreak:blood) is a thin layer lying on the ground, in three sizes (a few drops, a
splatter, a pool) and four ages: fresh and glossy, wet, dark and clotting, dried brown. Each size has
its own irregular shape (32x32 textures, soft edges, so it renders translucent); the block state
turns it four ways so neighbouring puddles don't repeat.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"
SIZE = 32

# Base colour, edge colour, gloss strength and how see-through it is, for each age.
AGES = [
    ((128, 6, 8), (92, 4, 6), 1.0, 235),      # fresh: deep bright red, glossy
    ((104, 8, 8), (70, 6, 6), 0.6, 225),      # wet
    ((66, 10, 10), (48, 10, 8), 0.25, 210),   # dark, clotting
    ((60, 30, 22), (44, 24, 18), 0.0, 170),   # dried brown stain
]


def blob_mask(rnd, size, kind):
    """An irregular splash: a main body made of overlapping lobes, droplets flung around it."""
    mask = Image.new("L", (SIZE, SIZE), 0)
    px = mask.load()
    c = SIZE / 2
    lobes = []
    if kind == 0:      # drops: a few small spots
        for _ in range(rnd.randint(5, 8)):
            lobes.append((rnd.uniform(5, 27), rnd.uniform(5, 27), rnd.uniform(1.0, 2.6)))
    elif kind == 1:    # splatter: a body, thrown out one way, with drops
        angle = rnd.uniform(0, 2 * math.pi)
        for i in range(7):
            d = i * 1.6
            lobes.append((c + math.cos(angle) * d + rnd.uniform(-1.5, 1.5), c + math.sin(angle) * d + rnd.uniform(-1.5, 1.5), 5.5 - i * 0.55))
        for _ in range(9):
            a = angle + rnd.uniform(-0.8, 0.8)
            d = rnd.uniform(9, 14)
            lobes.append((c + math.cos(a) * d, c + math.sin(a) * d, rnd.uniform(0.7, 1.6)))
    else:              # pool: a wide body with a ragged edge
        for _ in range(12):
            a = rnd.uniform(0, 2 * math.pi)
            d = rnd.uniform(0, 6)
            lobes.append((c + math.cos(a) * d, c + math.sin(a) * d, rnd.uniform(6, 9)))
        for _ in range(6):
            a = rnd.uniform(0, 2 * math.pi)
            lobes.append((c + math.cos(a) * 13, c + math.sin(a) * 13, rnd.uniform(0.8, 1.8)))
    for y in range(SIZE):
        for x in range(SIZE):
            v = 0.0
            for lx, ly, r in lobes:
                v += max(0.0, 1.0 - math.hypot(x - lx, y - ly) / r) ** 1.5 * 1.6
            px[x, y] = max(0, min(255, int(v * 255)))
    return mask.filter(ImageFilter.GaussianBlur(0.6))


def puddle(rnd, mask, age):
    base, edge, gloss, alpha = AGES[age]
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    m = mask.load()
    out = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            v = m[x, y] / 255.0
            if v < 0.18:
                continue
            # Thicker in the middle: darker; thin at the edge: lighter and more see-through.
            depth = min(1.0, (v - 0.18) / 0.6)
            col = tuple(int(edge[i] + (base[i] - edge[i]) * (1 - depth * 0.35)) for i in range(3))
            n = rnd.randint(-6, 6)
            col = tuple(max(0, min(255, col[i] + n)) for i in range(3))
            a = int(alpha * min(1.0, 0.45 + depth))
            out[x, y] = col + (a,)
    # Gloss: a few pale streaks on the fresh ones, where the light catches.
    if gloss > 0:
        for _ in range(int(14 * gloss)):
            x, y = rnd.randrange(SIZE), rnd.randrange(SIZE)
            if m[x, y] > 170:
                for dx in range(rnd.randint(1, 3)):
                    if x + dx < SIZE and m[x + dx, y] > 170:
                        c = out[x + dx, y]
                        out[x + dx, y] = tuple(min(255, c[i] + int(70 * gloss)) for i in range(3)) + (c[3],)
    # Dried ones crack and flake.
    if age == 3:
        for _ in range(40):
            x, y = rnd.randrange(SIZE), rnd.randrange(SIZE)
            if out[x, y][3] > 0:
                out[x, y] = (0, 0, 0, 0) if rnd.random() < 0.5 else (34, 18, 14, out[x, y][3])
    return img


WALL_SHAPES = 4


def wall_mask(rnd, size):
    """Blood thrown against a wall: a splash, and runs dripping down from it."""
    mask = Image.new("L", (SIZE, SIZE), 0)
    px = mask.load()
    lobes = []
    cx, cy = rnd.uniform(9, 23), rnd.uniform(5, 15)
    for _ in range(5 + size * 4):
        lobes.append((cx + rnd.uniform(-5, 5) * (1 + size * 0.6), cy + rnd.uniform(-3, 3), rnd.uniform(1.5, 3.5 + size)))
    for _ in range(8 + size * 6):
        a = rnd.uniform(0, 2 * math.pi)
        d = rnd.uniform(6, 12 + size * 2)
        lobes.append((cx + math.cos(a) * d, cy + math.sin(a) * d * 0.7, rnd.uniform(0.5, 1.2)))
    runs = [(cx + rnd.uniform(-6, 6) * (1 + size * 0.5), cy + 1, rnd.uniform(8, 18 + size * 6), rnd.uniform(0.6, 1.1)) for _ in range(2 + size * 2)]
    for y in range(SIZE):
        for x in range(SIZE):
            v = 0.0
            for lx, ly, r in lobes:
                v += max(0.0, 1.0 - math.hypot(x - lx, y - ly) / r) ** 1.5 * 1.6
            for rx, ry, length, width in runs:
                if ry <= y <= ry + length:
                    t = (y - ry) / length
                    v += max(0.0, 1.0 - abs(x - rx) / (width * (1.0 - t * 0.5))) * (1.2 if y < ry + length - 1 else 2.0)
            px[x, y] = max(0, min(255, int(v * 255)))
    return mask.filter(ImageFilter.GaussianBlur(0.5))


def wall_model(name):
    tex = f"outbreak:block/{name}"
    return {
        "ambientocclusion": False,
        "textures": {"particle": "outbreak:block/blood_particle", "blood": tex},
        "elements": [{
            "from": [0, 0, 0.05], "to": [16, 16, 0.15],
            "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#blood"}},
        }],
    }


def model(size, age):
    tex = f"outbreak:block/blood_{size}_{age}"
    return {
        "ambientocclusion": False,
        "textures": {"particle": "outbreak:block/blood_particle", "blood": tex},
        "elements": [{
            "from": [0, 0.05, 0], "to": [16, 0.15, 16],
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#blood"}},
        }],
    }


def main():
    rnd = random.Random(11)
    textures = ROOT / "textures/block"
    models = ROOT / "models/block"
    textures.mkdir(parents=True, exist_ok=True)
    models.mkdir(parents=True, exist_ok=True)
    for size in range(3):
        mask = blob_mask(rnd, size, size)
        for age in range(4):
            puddle(random.Random(size * 10 + age), mask, age).save(textures / f"blood_{size}_{age}.png")
            (models / f"blood_{size}_{age}.json").write_text(json.dumps(model(size, age), indent="\t") + "\n")
    # The particle (and the chunks a hit sprays): dark red, glistening.
    particle = Image.new("RGBA", (16, 16))
    prnd = random.Random(3)
    for y in range(16):
        for x in range(16):
            v = prnd.randint(-14, 14)
            particle.putpixel((x, y), (max(0, 120 + v), max(0, 8 + v // 3), max(0, 10 + v // 3), 255))
    particle.save(textures / "blood_particle.png")
    variants = {}
    for size in range(3):
        for age in range(4):
            for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                variants[f"age={age},facing={facing},size={size}"] = {"model": f"outbreak:block/blood_{size}_{age}", "y": y}
    (ROOT / "blockstates/blood.json").write_text(json.dumps({"variants": variants}, indent="\t") + "\n")
    # Blood on walls: against the north side of the block, facing south (turned by the block state).
    # A few shapes for each size, picked at random per block, so a wall doesn't look tiled.
    for old in list(textures.glob("wall_blood_*.png")) + list(models.glob("wall_blood_*.json")):
        old.unlink()
    variants = {}
    for size in range(2):
        for age in range(4):
            for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
                variants[f"age={age},facing={facing},size={size}"] = []
        for shape in range(WALL_SHAPES):
            mask = wall_mask(random.Random(50 + size + shape * 7), size)
            for age in range(4):
                name = f"wall_blood_{size}_{shape}_{age}"
                puddle(random.Random(60 + size * 10 + age + shape * 100), mask, age).save(textures / f"{name}.png")
                (models / f"{name}.json").write_text(json.dumps(wall_model(name), indent="\t") + "\n")
                for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
                    variants[f"age={age},facing={facing},size={size}"].append({"model": f"outbreak:block/{name}", "y": y})
    (ROOT / "blockstates/wall_blood.json").write_text(json.dumps({"variants": variants}, indent="\t") + "\n")
    print("wrote the blood textures, models and blockstates (floor and walls)")


if __name__ == "__main__":
    main()
