"""Generates the menu art: the title panorama, the logo and the dark widget skins.

Run from the repo root:  python3 tools/menu/make_menu.py

- The panorama (every menu's background, slowly turning) is Groningen burning at dusk: the
  Martinitoren, the Der Aa-kerk, the Groninger Museum, gabled canal houses, wind turbines on the
  flat land, smoke, abandoned cars and bicycles, and walkers in the street. It's painted as one
  360-degree strip and then projected onto the six faces of the cube the game draws it on.
- The logo is a blocky OUTBREAK with blood running down it, and GRONINGEN under it.
- The widgets (buttons, sliders, text fields, list backgrounds) become dark charcoal with a worn
  tan border, gold when the mouse is over them, like the HUD.
"""
import json
import math
import random
from pathlib import Path

import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "textures"))
import hudart  # noqa: E402  (the dark-paper style of the HUD and screens)

RES = Path(__file__).resolve().parents[2] / "src/main/resources/assets"
MC = RES / "minecraft/textures/gui"
OUT = RES / "outbreak/textures/gui"

STRIP_W, STRIP_H = 4096, 1024   # 360 degrees wide; t = tan(elevation) from +1 (top) to -1 (bottom)
FACE = 1024


# --- The strip -------------------------------------------------------------------------------

def px(deg, t):
    """Strip pixel for an azimuth (degrees) and t = tan(elevation)."""
    return deg / 360.0 * STRIP_W, (1.0 - t) / 2.0 * STRIP_H


def poly(draw, points, fill):
    """A polygon in (degrees, t), drawn again one turn left and right so it wraps."""
    for shift in (-360, 0, 360):
        draw.polygon([px(d + shift, t) for d, t in points], fill=fill)


def rect(draw, d0, d1, t0, t1, fill):
    poly(draw, [(d0, t0), (d1, t0), (d1, t1), (d0, t1)], fill)


def ellipse(draw, d, t, rd, rt, fill):
    for shift in (-360, 0, 360):
        x0, y0 = px(d - rd + shift, t + rt)
        x1, y1 = px(d + rd + shift, t - rt)
        draw.ellipse([x0, y0, x1, y1], fill=fill)


def lerp(a, b, f):
    return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(len(a)))


def sky(rnd):
    img = np.zeros((STRIP_H, STRIP_W, 4), dtype=np.float32)
    stops = [(1.0, (8, 8, 15)), (0.45, (22, 14, 22)), (0.18, (64, 24, 24)), (0.05, (128, 44, 24)), (0.0, (156, 62, 30)), (-0.05, (60, 26, 20)), (-1.0, (8, 6, 7))]
    for y in range(STRIP_H):
        t = 1.0 - 2.0 * y / STRIP_H
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t1 <= t <= t0:
                f = (t0 - t) / (t0 - t1)
                img[y, :, :3] = lerp(c0, c1, f)
                break
    img[:, :, 3] = 255
    # A warmer glow behind the burning parts of town.
    for centre, strength in ((20, 1.0), (140, 0.6), (235, 0.8), (320, 0.5)):
        xs = np.arange(STRIP_W) / STRIP_W * 360.0
        dist = np.minimum(np.abs(xs - centre), 360 - np.abs(xs - centre))
        glow = np.exp(-(dist / 35.0) ** 2) * strength
        for y in range(STRIP_H // 2 - 260, STRIP_H // 2 + 40):
            t = 1.0 - 2.0 * y / STRIP_H
            k = glow * math.exp(-max(t, 0) / 0.18) * 0.55
            img[y, :, 0] += 120 * k
            img[y, :, 1] += 40 * k
            img[y, :, 2] += 10 * k
    noise = np.random.default_rng(3).normal(0, 2.0, (STRIP_H, STRIP_W, 1))
    img[:, :, :3] += noise
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")
    draw = ImageDraw.Draw(out)
    # Stars, high up where the smoke doesn't reach.
    for _ in range(500):
        d, t = rnd.uniform(0, 360), rnd.uniform(0.42, 1.0)
        b = rnd.randint(70, 170)
        x, y = px(d, t)
        draw.point((x, y), fill=(b, b, b + 10, 255))
    # The moon, hazy.
    halo = Image.new("RGBA", out.size, (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    ellipse(hd, 205, 0.58, 4.5, 0.11, (150, 120, 110, 60))
    halo = halo.filter(ImageFilter.GaussianBlur(18))
    out.alpha_composite(halo)
    ellipse(draw, 205, 0.58, 1.6, 0.045, (214, 196, 178, 255))
    ellipse(draw, 205.5, 0.585, 0.5, 0.012, (180, 160, 146, 255))
    return out


def smoke(rnd):
    layer = Image.new("RGBA", (STRIP_W, STRIP_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for base, lean, height in ((22, 18, 0.62), (141, -10, 0.45), (236, 14, 0.55), (318, 8, 0.4), (74, 6, 0.3)):
        steps = 60
        for i in range(steps):
            f = i / steps
            t = 0.04 + f * height
            d = base + lean * f * f + rnd.uniform(-1.0, 1.0)
            r = 1.5 + 9.0 * f
            shade = int(30 + 20 * (1 - f))
            ellipse(draw, d, t, r, r * 0.014 + 0.025, (shade + 16, shade, shade, int(200 * (1 - f) + 40)))
    return layer.filter(ImageFilter.GaussianBlur(10))


def gabled_house(draw, d, w, h, fill, rnd, windows=None):
    """A Dutch canal house: a stepped or bell gable on top of a narrow front."""
    roof = h * rnd.uniform(0.25, 0.4)
    body = h - roof
    pts = [(d, 0.0), (d, body)]
    kind = rnd.choice(["step", "step", "bell", "neck"])
    if kind == "step":
        steps = 3
        for i in range(steps):
            x = d + w * (i / (2 * steps))
            y = body + roof * (i / steps)
            pts += [(x, y), (x, y + roof / steps)]
        pts += [(d + w / 2 - w * 0.08, h), (d + w / 2 + w * 0.08, h)]
        for i in reversed(range(steps)):
            x = d + w - w * (i / (2 * steps))
            y = body + roof * (i / steps)
            pts += [(x, y + roof / steps), (x, y)]
    elif kind == "bell":
        for i in range(9):
            a = math.pi * i / 8
            pts.append((d + w / 2 - math.cos(a) * w / 2 * (0.6 + 0.4 * math.sin(a)), body + math.sin(a) * roof))
    else:
        pts += [(d + w * 0.25, body), (d + w * 0.25, body + roof * 0.7), (d + w * 0.4, h), (d + w * 0.6, h), (d + w * 0.75, body + roof * 0.7), (d + w * 0.75, body)]
    pts += [(d + w, body), (d + w, 0.0)]
    poly(draw, pts, fill)
    if windows is not None:
        rows = max(1, int(body / 0.022))
        for row in range(rows):
            for col in range(2):
                if rnd.random() < 0.18:
                    wd = d + w * (0.25 + col * 0.35)
                    wt = 0.01 + row * body / rows
                    windows.append((wd, wt, w * 0.12, body / rows * 0.45))


def martinitoren(draw, d, fill):
    # The Martinitoren: a square tower in stages, each narrower, a lantern and a little dome with a spire.
    stages = [(0.0, 0.3, 6.0), (0.3, 0.41, 4.8), (0.41, 0.49, 3.8), (0.49, 0.55, 2.8), (0.55, 0.59, 2.0)]
    for t0, t1, w in stages:
        rect(draw, d - w / 2, d + w / 2, t0, t1, fill)
        rect(draw, d - w / 2 - 0.12, d + w / 2 + 0.12, t1 - 0.004, t1, fill)  # ledge
    poly(draw, [(d - 0.7, 0.59), (d - 0.45, 0.625), (d, 0.645), (d + 0.45, 0.625), (d + 0.7, 0.59)], fill)
    rect(draw, d - 0.07, d + 0.07, 0.645, 0.7, fill)
    rect(draw, d - 0.25, d + 0.25, 0.68, 0.686, fill)  # the cross bar of the weathervane
    # The church behind it.
    poly(draw, [(d + 1.0, 0.0), (d + 1.0, 0.12), (d + 4.5, 0.17), (d + 8.0, 0.12), (d + 8.0, 0.0)], fill)


def aa_kerk(draw, d, fill):
    poly(draw, [(d - 4, 0.0), (d - 4, 0.09), (d - 1, 0.13), (d - 1, 0.0)], fill)
    rect(draw, d - 0.8, d + 0.8, 0.0, 0.19, fill)
    poly(draw, [(d - 0.8, 0.19), (d, 0.33), (d + 0.8, 0.19)], fill)
    rect(draw, d - 0.03, d + 0.03, 0.33, 0.36, fill)


def museum(draw, d, fill):
    # The Groninger Museum: stacked blocks, one of them tilted.
    rect(draw, d, d + 3, 0.0, 0.1, fill)
    rect(draw, d + 0.8, d + 2.0, 0.1, 0.17, fill)
    poly(draw, [(d + 3, 0.0), (d + 3.2, 0.13), (d + 5.6, 0.16), (d + 5.4, 0.0)], fill)


def turbine(draw, d, h, rot, fill):
    rect(draw, d - 0.05, d + 0.05, 0.0, h, fill)
    for k in range(3):
        a = rot + k * 2 * math.pi / 3
        tip_d, tip_t = d + math.cos(a) * 1.1, h + math.sin(a) * 0.035
        poly(draw, [(d, h + 0.002), (tip_d, tip_t), (d + math.cos(a + 0.2) * 0.2, h + math.sin(a + 0.2) * 0.006)], fill)


def walker(draw, d, feet, size, facing, rnd, fill):
    """A walker's silhouette: hunched, head lolling, arms reaching out ahead."""
    s = size
    f = facing
    k = s * 70  # degrees per unit of body size (about as wide as it is tall, seen from here)
    hip = feet + s * 0.48
    shoulder = hip + s * 0.32
    lean = rnd.uniform(0.04, 0.1) * k * f
    # Legs, one dragging behind.
    poly(draw, [(d - 0.07 * k, feet), (d - 0.06 * k, hip), (d + 0.02 * k, hip), (d - 0.01 * k, feet)], fill)
    poly(draw, [(d + 0.0 * k, hip), (d + 0.07 * k, hip), (d - 0.1 * k * f, feet + s * 0.03), (d - 0.14 * k * f, feet)], fill)
    # Torso, hunched forward.
    cx = d + lean
    poly(draw, [(d - 0.08 * k, hip), (cx - 0.09 * k, shoulder), (cx + 0.1 * k, shoulder - s * 0.02), (d + 0.08 * k, hip)], fill)
    # Head, tilted.
    ellipse(draw, cx + 0.05 * k * f, shoulder + s * 0.065, 0.045 * k, s * 0.06, fill)
    # Arms reaching forward (one higher than the other).
    for lift in (0.0, rnd.uniform(-0.08, 0.05)):
        hand_d = cx + f * k * rnd.uniform(0.28, 0.36)
        hand_t = shoulder - s * (0.04 + lift)
        poly(draw, [(cx - 0.02 * k * f, shoulder), (hand_d, hand_t + s * 0.03), (hand_d, hand_t - s * 0.02), (cx, shoulder - s * 0.09)], fill)


def car(draw, d, w, ground, h, fill, wreck=False):
    poly(draw, [(d, ground + h * 0.15), (d, ground + h * 0.55), (d + w * 0.2, ground + h * 0.6), (d + w * 0.32, ground + h), (d + w * 0.72, ground + h),
                (d + w * 0.85, ground + h * 0.6), (d + w, ground + h * 0.5), (d + w, ground + h * 0.15)], fill)
    for wd in (d + w * 0.2, d + w * 0.8):
        if not wreck:
            ellipse(draw, wd, ground + h * 0.15, w * 0.09, h * 0.16, fill)


def bicycle(draw, d, ground, s, fill):
    r = s * 0.35
    for wd in (d, d + s * 40 * 0.9):
        for shift in (-360, 0, 360):
            x0, y0 = px(wd - r * 40 + shift, ground + 2 * r)
            x1, y1 = px(wd + r * 40 + shift, ground)
            draw.ellipse([x0, y0, x1, y1], outline=fill, width=2)
    for a, b in (((d, ground + r), (d + s * 18, ground + r * 2.0)), ((d + s * 18, ground + r * 2.0), (d + s * 36, ground + r)), ((d + s * 14, ground + r * 2.3), (d + s * 22, ground + r * 2.3))):
        for shift in (-360, 0, 360):
            draw.line([px(a[0] + shift, a[1]), px(b[0] + shift, b[1])], fill=fill, width=3)


def lamppost(draw, d, ground, h, lit, fill, glow_layer):
    rect(draw, d - 0.07, d + 0.07, ground, ground + h, fill)
    rect(draw, d - 0.07, d + 0.9, ground + h - 0.006, ground + h, fill)
    if lit:
        gd = ImageDraw.Draw(glow_layer)
        ellipse(gd, d + 0.8, ground + h - 0.05, 2.0, 0.1, (255, 190, 110, 28))
        ellipse(gd, d + 0.8, ground + h - 0.012, 0.3, 0.008, (255, 230, 170, 255))


def bare_tree(draw, d, ground, h, rnd, fill):
    rect(draw, d - 0.12, d + 0.12, ground, ground + h * 0.45, fill)

    def branch(x, t, angle, length, width):
        if length < 0.01:
            return
        nx = x + math.cos(angle) * length * 30
        nt = t + math.sin(angle) * length
        for shift in (-360, 0, 360):
            draw.line([px(x + shift, t), px(nx + shift, nt)], fill=fill, width=max(1, int(width)))
        for _ in range(2):
            branch(nx, nt, angle + rnd.uniform(-0.6, 0.6), length * rnd.uniform(0.55, 0.75), width * 0.65)

    branch(d, ground + h * 0.45, math.pi / 2, h * 0.3, 8)


def strip(seed=11):
    rnd = random.Random(seed)
    img = sky(rnd)
    img.alpha_composite(smoke(rnd))

    # Far away: the flat land, wind turbines, a low line of distant buildings.
    far = Image.new("RGBA", img.size, (0, 0, 0, 0))
    fd = ImageDraw.Draw(far)
    far_fill = (52, 30, 32, 255)
    d = 0.0
    while d < 360:
        w = rnd.uniform(1.5, 6)
        rect(fd, d, d + w, 0.0, rnd.uniform(0.008, 0.03), far_fill)
        d += w
    for td in (48, 55, 61, 168, 176, 262, 271, 282, 345):
        turbine(fd, td, rnd.uniform(0.07, 0.1), rnd.uniform(0, 2 * math.pi), far_fill)
    img.alpha_composite(far)

    # The city: the landmarks and rows of gabled houses, lit windows here and there.
    city = Image.new("RGBA", img.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(city)
    city_fill = (26, 16, 19, 255)
    windows = []
    d = 0.0
    while d < 360:
        w = rnd.uniform(1.3, 2.4)
        gabled_house(cd, d, w, rnd.uniform(0.07, 0.15), city_fill, rnd, windows)
        d += w + rnd.uniform(0.0, 0.25)
    # Blocks of flats out towards Paddepoel.
    for fd0 in (250, 258, 290):
        rect(cd, fd0, fd0 + 5, 0.0, rnd.uniform(0.16, 0.22), city_fill)
        for row in range(10):
            for col in range(6):
                if rnd.random() < 0.08:
                    windows.append((fd0 + 0.4 + col * 0.75, 0.012 + row * 0.019, 0.35, 0.007))
    martinitoren(cd, 18, city_fill)
    aa_kerk(cd, 330, city_fill)
    museum(cd, 128, city_fill)
    img.alpha_composite(city)
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for wd, wt, ww, wh in windows:
        warm = rnd.choice([(220, 130, 50, 255), (240, 170, 70, 255), (200, 90, 40, 255)])
        rect(ImageDraw.Draw(img), wd, wd + ww, wt, wt + wh, warm)
        ellipse(gd, wd + ww / 2, wt + wh / 2, ww * 2, wh * 2.5, warm[:3] + (70,))
    # Fires: flames licking up from a few roofs.
    flames = Image.new("RGBA", img.size, (0, 0, 0, 0))
    fl = ImageDraw.Draw(flames)
    for fire_d in (24, 142, 236, 318):
        for _ in range(40):
            fdx = fire_d + rnd.uniform(-1.8, 1.8)
            top = rnd.uniform(0.12, 0.2)
            poly(fl, [(fdx - 0.25, 0.1), (fdx + rnd.uniform(-0.2, 0.2), top), (fdx + 0.25, 0.1)], (255, rnd.randint(90, 180), 30, 200))
        ellipse(gd, fire_d, 0.12, 14, 0.05, (255, 110, 40, 45))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(14)))
    img.alpha_composite(flames.filter(ImageFilter.GaussianBlur(1.5)))

    # Haze over the city towards the horizon.
    haze = np.zeros((STRIP_H, STRIP_W, 4), dtype=np.uint8)
    for y in range(STRIP_H // 2 - 80, STRIP_H // 2 + 10):
        t = 1.0 - 2.0 * y / STRIP_H
        a = int(70 * math.exp(-abs(t) / 0.05))
        haze[y, :, :] = (120, 50, 30, a)
    img.alpha_composite(Image.fromarray(haze, "RGBA"))

    # Close by, the street: below the horizon, with cars, bicycles, lampposts, trees and walkers.
    street = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(street)
    near_fill = (3, 2, 3, 255)
    light = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for cd0, w, ground, h, wreck in ((40, 14, -0.14, 0.1, False), (95, 26, -0.38, 0.24, True), (190, 12, -0.1, 0.08, False), (296, 20, -0.26, 0.17, False)):
        car(sd, cd0, w, ground, h, near_fill, wreck)
    for bd in (150, 152.5, 155, 157.5, 160):
        bicycle(sd, bd, -0.05, 0.05, near_fill)
    for ld, lit, lg in ((10, True, -0.12), (70, False, -0.3), (125, True, -0.18), (210, False, -0.1), (275, True, -0.22), (345, False, -0.14)):
        lamppost(sd, ld, lg, 0.3 - lg * 1.8, lit, near_fill, light)
    for td in (32, 112, 178, 228, 312):
        bare_tree(sd, td, -0.08, rnd.uniform(0.35, 0.5), rnd, near_fill)
    # The dead, shuffling about: small far off, bigger near.
    for wd in (5, 28, 33, 58, 86, 91, 117, 135, 166, 201, 207, 222, 248, 266, 289, 304, 333, 351):
        dist = rnd.uniform(0, 1) ** 2
        size = 0.1 + 0.55 * dist
        feet = -0.02 - 0.5 * dist
        walker(sd, wd, feet, size, rnd.choice([-1, 1]), rnd, near_fill)

    # The ground: dark, with puddles catching the firelight; the street things stand on it.
    ground = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gdr = ImageDraw.Draw(ground)
    rect(gdr, 0, 360, -1.0, -0.004, (12, 9, 10, 255))
    # Cobbles: rows of little stones, bigger and further apart close by.
    t = -0.01
    while t > -1.0:
        size = abs(t)
        d = rnd.uniform(0, 2)
        while d < 360:
            ellipse(gdr, d, t, 0.25 + size * 3.5, 0.0015 + size * 0.018, (17 + rnd.randint(-3, 3), 13, 13, 255))
            d += 0.7 + size * 9
        t -= 0.004 + size * 0.05
    for _ in range(45):
        pd, pt = rnd.uniform(0, 360), rnd.uniform(-0.7, -0.03)
        ellipse(gdr, pd, pt, rnd.uniform(1, 4) * (1 + abs(pt) * 4), 0.003 + abs(pt) * 0.012, (80, 34, 20, rnd.randint(50, 100)))
    img.alpha_composite(ground.filter(ImageFilter.GaussianBlur(1.2)))
    img.alpha_composite(street)
    img.alpha_composite(light.filter(ImageFilter.GaussianBlur(9)))
    return img


# --- Projection onto the cube ----------------------------------------------------------------

def faces(src):
    arr = np.asarray(src.convert("RGB"), dtype=np.float32)
    h, w, _ = arr.shape
    out = []
    # Faces 0-3 look north, east, south, west (each 90 degrees further to the right); 4 up, 5 down.
    u = (np.arange(FACE) + 0.5) / FACE * 2 - 1
    uu, vv = np.meshgrid(u, -u)
    for i in range(4):
        az = (i * 90 + np.degrees(np.arctan(uu))) % 360
        t = vv / np.sqrt(1 + uu * uu)
        out.append(sample(arr, az, t))
    # Up: straight overhead is t = infinity; use the sky colour at the top of the strip, darkening.
    top = np.zeros((FACE, FACE, 3), dtype=np.float32)
    r = np.sqrt(uu * uu + vv * vv)
    top[:, :] = arr[0, 0]
    top *= (0.8 + 0.2 * np.clip(r, 0, 1))[:, :, None]
    out.append(top)
    bottom = np.full((FACE, FACE, 3), arr[-1, 0], dtype=np.float32)
    out.append(bottom)
    return [Image.fromarray(np.clip(f, 0, 255).astype(np.uint8), "RGB") for f in out]


def sample(arr, az, t):
    h, w, _ = arr.shape
    x = az / 360.0 * w
    y = np.clip((1.0 - t) / 2.0 * h, 0, h - 1.001)
    x0 = np.floor(x).astype(int) % w
    x1 = (x0 + 1) % w
    y0 = np.floor(y).astype(int)
    y1 = np.minimum(y0 + 1, h - 1)
    fx = (x - np.floor(x))[:, :, None]
    fy = (y - y0)[:, :, None]
    top = arr[y0, x0] * (1 - fx) + arr[y0, x1] * fx
    bot = arr[y1, x0] * (1 - fx) + arr[y1, x1] * fx
    return top * (1 - fy) + bot * fy


# --- Logo ------------------------------------------------------------------------------------

LETTERS = {
    "O": [".####.", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "U": ["##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."],
    "T": ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##.."],
    "B": ["#####.", "##..##", "##..##", "#####.", "##..##", "##..##", "#####."],
    "R": ["#####.", "##..##", "##..##", "#####.", "##.##.", "##..##", "##..##"],
    "E": ["######", "##....", "##....", "#####.", "##....", "##....", "######"],
    "A": [".####.", "##..##", "##..##", "######", "##..##", "##..##", "##..##"],
    "K": ["##..##", "##.##.", "####..", "###...", "####..", "##.##.", "##..##"],
    "G": [".####.", "##..##", "##....", "##.###", "##..##", "##..##", ".####."],
    "N": ["##..##", "###.##", "######", "##.###", "##..##", "##..##", "##..##"],
    "I": ["######", "..##..", "..##..", "..##..", "..##..", "..##..", "######"],
}


def word(text, scale, spacing):
    w = len(text) * (6 + spacing) - spacing
    mask = Image.new("L", (w * scale, 7 * scale), 0)
    d = ImageDraw.Draw(mask)
    for i, ch in enumerate(text):
        for y, row in enumerate(LETTERS[ch]):
            for x, c in enumerate(row):
                if c == "#":
                    x0 = (i * (6 + spacing) + x) * scale
                    d.rectangle([x0, y * scale, x0 + scale - 1, y * scale + scale - 1], fill=255)
    return mask


def logo():
    rnd = random.Random(5)
    scale = 5
    title = word("OUTBREAK", scale, 1)
    sub = word("GRONINGEN", 2, 2)
    width = title.width + 16
    height = title.height + 40 + sub.height
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ox, oy = 8, 6
    # Blood running down from the letters.
    drips = Image.new("L", (width, height), 0)
    dd = ImageDraw.Draw(drips)
    tw, th = title.size
    tp = title.load()
    for x in range(0, tw, scale):
        if tp[x + 1, th - 2] and rnd.random() < 0.35:
            length = rnd.randint(4, 22)
            dd.rectangle([ox + x + 1, oy + th - 2, ox + x + scale - 2, oy + th + length], fill=255)
            dd.ellipse([ox + x, oy + th + length - 2, ox + x + scale - 1, oy + th + length + 3], fill=255)
    # Letters: bone white at the top, soaked red towards the bottom.
    letters = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    lp = letters.load()
    for y in range(th):
        f = y / th
        col = lerp((226, 216, 196), (150, 20, 18), max(0.0, (f - 0.35) / 0.65))
        for x in range(tw):
            if tp[x, y]:
                n = rnd.randint(-10, 10)
                lp[ox + x, oy + y] = (max(0, col[0] + n), max(0, col[1] + n // 2), max(0, col[2] + n // 2), 255)
    # Cracks and chips.
    for _ in range(140):
        x, y = rnd.randrange(tw), rnd.randrange(th)
        if tp[x, y]:
            lp[ox + x, oy + y] = (40, 10, 10, 255) if rnd.random() < 0.5 else (0, 0, 0, 0)
    blood = Image.new("RGBA", (width, height), (120, 10, 10, 255))
    blood.putalpha(drips)
    # A dark outline around everything, so it reads over the bright parts of the sky.
    shape = Image.new("L", (width, height), 0)
    shape.paste(255, (ox, oy), title)
    shape = Image.composite(Image.new("L", shape.size, 255), shape, drips)
    outline = shape.filter(ImageFilter.MaxFilter(5))
    img.paste((14, 8, 8, 255), (0, 0), outline)
    img.alpha_composite(blood)
    img.alpha_composite(letters)
    # GRONINGEN, spaced out under it.
    sx = (width - sub.width) // 2
    sy = oy + th + 26
    sub_outline = Image.new("L", (width, height), 0)
    sub_outline.paste(255, (sx, sy), sub)
    img.paste((14, 8, 8, 255), (0, 0), sub_outline.filter(ImageFilter.MaxFilter(3)))
    img.paste((217, 194, 122, 255), (sx, sy), sub)
    return img


# --- Widgets ---------------------------------------------------------------------------------

CHARCOAL = (24, 21, 19)
BORDER = (112, 98, 76)
GOLD = (217, 194, 122)


def widget(w, h, fill, border, seed, alpha=235, inner=None):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), fill + (alpha,))
    px_ = img.load()
    for y in range(h):
        for x in range(w):
            n = rnd.randint(-4, 4)
            c = px_[x, y]
            px_[x, y] = (max(0, c[0] + n), max(0, c[1] + n), max(0, c[2] + n), c[3])
    for x in range(w):
        for y in (0, h - 1):
            worn = rnd.random() < 0.12
            px_[x, y] = (border if not worn else lerp(border, fill, 0.6)) + (255,)
    for y in range(h):
        for x in (0, w - 1):
            px_[x, y] = border + (255,)
    if inner:
        for x in range(1, w - 1):
            px_[x, 1] = inner + (255,)
    return img


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path.relative_to(RES.parents[3]))


def nine_slice(path, w, h, border):
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}
    path.with_name(path.name + ".mcmeta").write_text(json.dumps(meta, indent=4) + "\n")


def widgets():
    sprites = MC / "sprites/widget"
    for name, fill, border, inner, alpha in (
        ("button", CHARCOAL, BORDER, (58, 50, 42), 235),
        ("button_highlighted", (40, 33, 26), GOLD, (90, 76, 50), 245),
        ("button_disabled", (18, 16, 15), (58, 52, 44), None, 200),
        ("slider", (16, 14, 13), BORDER, None, 235),
        ("slider_highlighted", (16, 14, 13), GOLD, None, 245),
        ("text_field", (12, 11, 10), BORDER, None, 240),
        ("text_field_highlighted", (12, 11, 10), GOLD, None, 245),
    ):
        path = sprites / f"{name}.png"
        save(widget(200, 20, fill, border, hash(name) & 0xFFFF, alpha, inner), path)
        nine_slice(path, 200, 20, 3 if name.startswith(("button", "slider")) else 1)
    for name, border in (("slider_handle", BORDER), ("slider_handle_highlighted", GOLD)):
        path = sprites / f"{name}.png"
        save(widget(8, 20, (70, 60, 48), border, 7, 255, (110, 96, 72)), path)
        nine_slice(path, 8, 20, 2)
    # Behind lists and menus: dark, grainy, a little warm.
    for name, base, alpha in (("menu_background", (20, 17, 15), 255), ("menu_list_background", (12, 10, 9), 255),
                              ("inworld_menu_background", (14, 12, 11), 190), ("inworld_menu_list_background", (8, 7, 6), 160)):
        rnd = random.Random(len(name))
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                n = rnd.randint(-5, 5)
                img.putpixel((x, y), (base[0] + n, base[1] + n, base[2] + n, alpha))
        save(img, MC / f"{name}.png")


# The pages the menus are written on, in the HUD's dark-paper style.
PAGES = {"new_survivor": (400, 226), "dossier": (420, 232), "character_sheet": (300, 200)}


def pages():
    for i, (name, (w, h)) in enumerate(PAGES.items()):
        save(hudart.CHOSEN.panel(w, h, seed=60 + i), OUT / f"{name}.png")


def main():
    pages()
    for i, face in enumerate(faces(strip())):
        save(face, MC / f"title/background/panorama_{i}.png")
    save(logo(), OUT / "title/logo.png")
    widgets()


if __name__ == "__main__":
    main()
