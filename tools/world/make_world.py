"""Generates the assets of the world winding down (phase 6): the dead light fixture the city's lamps
turn into when the power goes, the portable radio, batteries, a bottle of tap water, and the sounds
(the NL-Alert tone, radio static, a fuel pump running), synthesized and encoded with ffmpeg.

Run from the repo root:  python3 tools/world/make_world.py
"""
import json
import math
import random
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "vehicles"))
import make_vehicles as mv  # noqa: E402  (the box model tools)
RATE = 44100

PALETTE = {
    "k": (24, 22, 20, 255), "d": (54, 52, 50, 255), "g": (92, 90, 86, 255), "G": (132, 130, 124, 255),
    "w": (200, 198, 190, 255), "W": (236, 234, 226, 255), "r": (170, 34, 28, 255), "R": (230, 70, 50, 255),
    "y": (200, 170, 60, 255), "Y": (240, 214, 110, 255), "b": (40, 70, 140, 255), "B": (90, 130, 200, 255),
    "c": (150, 190, 210, 255), "C": (200, 228, 238, 255), "u": (110, 160, 196, 255), "U": (160, 200, 226, 255),
    "o": (196, 110, 40, 255), "s": (70, 80, 70, 255), "t": (126, 120, 100, 255),
}

SPRITES = {
    "item/portable_radio": """
................
.............k..
............kG..
...........kG...
..kkkkkkkkkkkk..
..kddddddddddk..
..kdgkgkgdYYdk..
..kdkgkgkdYYdk..
..kdgkgkgddddk..
..kdkgkgkdGdGk..
..kdgkgkgddddk..
..kddddddddddk..
..kkkkkkkkkkkk..
...kk......kk...
................
................""",
    "item/portable_radio_on": """
................
.............k..
............kG..
...........kG...
..kkkkkkkkkkkk..
..kddddddddRdk..
..kdgkgkgdYYdk..
..kdkgkgkdYYdk..
..kdgkgkgddddk..
..kdkgkgkdGdGk..
..kdgkgkgddddk..
..kddddddddddk..
..kkkkkkkkkkkk..
...kk......kk...
................
................""",
    "item/batteries": """
................
................
.....kk...kk....
....kWWk.kWWk...
...kkkkkkkkkkk..
...koooookoook..
...kooookooook..
...kooookooook..
...kkkkkkkkkkk..
...kddddkddddk..
...kddddkddddk..
...kddddkddddk..
...kGGGGkGGGGk..
...kkkkkkkkkkk..
................
................""",
    "item/tap_water": """
................
.......kk.......
......kGGk......
.......kk.......
......kcck......
.....kcCcck.....
....kcccccck....
....kcUUUUck....
....kuUuuuuk....
....kuuuuuuk....
....kuuuuuCk....
....kCuuuuCk....
.....kkkkkk.....
................
................
................""",
    "item/walker_guts": """
................
................
.....kkk........
....krRrkk......
...krrRrrrk.....
..krrrrrsrrk....
..krsrrrrrrrk...
...krrrsrrrrrk..
....kkrrrrssrk..
......krrrrrrk..
.....krrsrrrk...
.....krrrrkk....
......kkkk......
................
................
................""",
    "gui/sprites/moodle/guts": """
................
................
.....kkkkkk.....
....krrrRrrk....
...krRrrrrsrk...
...krrkrrkrrk...
...krrrrrrrrk...
...krsrrrrRrk...
....krrkkrrk....
....kr.rr.rk....
.....k.kk.k.....
......r..r......
.....r....r.....
......r..r......
................
................""",
}


def sprite(name, art):
    rows = art.strip("\n").split("\n")
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), PALETTE[ch])
    out = ROOT / "textures" / f"{name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


def dead_light():
    """A light fixture with no power: a frosted panel in a metal frame, grey and dull."""
    rng = random.Random("dead_light")
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = x in (1, 14) or y in (1, 14)
            if edge:
                c = (70, 72, 74)
            elif inner:
                c = (104, 106, 108)
            else:
                v = 150 + rng.randint(-6, 6) - (8 if (x + y) % 5 == 0 else 0)
                c = (v, v - 2, v - 10)
            img.putpixel((x, y), c + (255,))
    out = ROOT / "textures/block/dead_light.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    write(ROOT / "models/block/dead_light.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "outbreak:block/dead_light"}})
    write(ROOT / "blockstates/dead_light.json", {"variants": {"": {"model": "outbreak:block/dead_light"}}})


def items():
    for name in ("batteries", "tap_water"):
        write(ROOT / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"outbreak:item/{name}"}})
        write(ROOT / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"outbreak:item/{name}"}})
    for name in ("portable_radio", "portable_radio_on"):
        write(ROOT / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"outbreak:item/{name}"}})
    # The radio shows its red light while it's on (custom model data flag 0).
    write(ROOT / "items/portable_radio.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
        "on_true": {"type": "minecraft:model", "model": "outbreak:item/portable_radio_on"},
        "on_false": {"type": "minecraft:model", "model": "outbreak:item/portable_radio"}}})


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent="\t") + "\n")


# --- Sounds -------------------------------------------------------------------------------------

def save_ogg(name, samples):
    samples = np.clip(samples, -1.0, 1.0)
    data = (samples * 32000).astype(np.int16)
    out = ROOT / "sounds" / f"{name}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(data.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4", str(out)], check=True)


def envelope(n, attack, release):
    env = np.ones(n)
    a, r = int(attack * RATE), int(release * RATE)
    env[:a] = np.linspace(0, 1, a)
    env[-r:] = np.linspace(1, 0, r)
    return env


def nl_alert():
    """The NL-Alert tone: a loud, harsh siren rising and falling, three times."""
    seconds = 4.5
    t = np.arange(int(seconds * RATE)) / RATE
    freq = 700 + 450 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 1.5))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    tone = np.sign(np.sin(phase)) * 0.35 + np.sin(phase * 2) * 0.25 + np.sin(phase * 3) * 0.1
    gate = (np.sin(2 * np.pi * t * 8) > -0.6).astype(float) * 0.25 + 0.75
    return tone * gate * envelope(len(t), 0.05, 0.3) * 0.7


def radio_static(seed):
    """A burst of radio static: hiss through a band-pass, crackles, a whistle drifting."""
    rng = np.random.default_rng(seed)
    seconds = 2.5
    n = int(seconds * RATE)
    noise = rng.normal(0, 1, n)
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    band = np.exp(-((freqs - 2200) / 1600) ** 2)
    hiss = np.fft.irfft(spectrum * band, n)
    hiss /= np.max(np.abs(hiss))
    t = np.arange(n) / RATE
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * t * (0.7 + seed * 0.13))
    crackle = np.zeros(n)
    for _ in range(40):
        at = rng.integers(0, n - 200)
        crackle[at:at + 60] += rng.normal(0, 0.8, 60) * np.exp(-np.arange(60) / 12)
    whistle = 0.08 * np.sin(2 * np.pi * np.cumsum(1800 + 600 * np.sin(2 * np.pi * t * 0.3)) / RATE)
    return (hiss * 0.45 * swell + crackle * 0.4 + whistle) * envelope(n, 0.05, 0.4) * 0.8


def pump():
    """A fuel pump running: a motor's hum and the fuel rushing, a click at the end."""
    seconds = 3.0
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(3)
    hum = sum(np.sin(2 * np.pi * 110 * k * t) / k for k in range(1, 6)) * 0.25
    hum *= 0.85 + 0.15 * np.sin(2 * np.pi * 7 * t)
    flow = rng.normal(0, 1, n)
    flow = np.convolve(flow, np.ones(30) / 30, mode="same") * 1.5
    click = np.zeros(n)
    click[-int(0.25 * RATE):-int(0.25 * RATE) + 400] = rng.normal(0, 1, 400) * np.exp(-np.arange(400) / 60)
    return (hum + flow * 0.3) * envelope(n, 0.2, 0.4) * 0.6 + click * 0.8


def power_out():
    """Somewhere a transformer goes: a deep bang, a buzz dying away."""
    seconds = 2.5
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(9)
    bang = rng.normal(0, 1, n) * np.exp(-t * 9)
    bang = np.convolve(bang, np.ones(80) / 80, mode="same") * 6
    thump = np.sin(2 * np.pi * 48 * t) * np.exp(-t * 4)
    buzz = np.sign(np.sin(2 * np.pi * 100 * t)) * 0.2 * np.exp(-t * 1.5) * (t > 0.15)
    return (bang + thump + buzz) * envelope(n, 0.005, 0.5) * 0.7


def sounds():
    save_ogg("nl_alert", nl_alert())
    for i in range(3):
        save_ogg(f"radio_static_{i}", radio_static(i))
    save_ogg("fuel_pump", pump())
    save_ogg("power_out", power_out())
    events = {
        "nl_alert": {"subtitle": "subtitles.outbreak.nl_alert", "sounds": ["outbreak:nl_alert"]},
        "radio_static": {"subtitle": "subtitles.outbreak.radio_static",
                         "sounds": [f"outbreak:radio_static_{i}" for i in range(3)]},
        "fuel_pump": {"subtitle": "subtitles.outbreak.fuel_pump", "sounds": ["outbreak:fuel_pump"]},
        "power_out": {"subtitle": "subtitles.outbreak.power_out", "sounds": [{"name": "outbreak:power_out", "attenuation_distance": 64}]},
    }
    sounds_json = ROOT / "sounds.json"
    existing = json.loads(sounds_json.read_text()) if sounds_json.exists() else {}
    existing.update(events)
    write(sounds_json, existing)


# --- Phase 6b: the helicopter, the flare, walker guts ------------------------------------------

def helicopter_parts():
    """An army helicopter (an NH90, grey-green): cabin, nose, tail boom and fin, engines on top, the
    main rotor and the tail rotor (both spin), skids. In pixels, y up, z forward."""
    B, box, Part = mv.Box, mv.box, mv.Part
    body = Part("body", boxes=[
        box(-16, 6, -24, 16, 40, 30, {"all": "hull", "left": ("hull_side", -24, 30), "right": ("hull_side", -24, 30), "bottom": "hull_dark"}),
        box(-12, 8, 30, 12, 32, 44, {"all": "hull", "bottom": "hull_dark", "front": "nose"}),
        box(-11, 22, 34, 11, 33, 45, {"all": "cockpit", "bottom": "hull"}, inflate=0.3),
        box(-10, 40, -18, 10, 48, 16, {"all": "engine", "top": "engine_top"}),
        box(-2, 48, -2, 2, 54, 2, "metal"),
        box(-5, 25, -82, 5, 35, -24, {"all": "hull", "left": "boom_side", "right": "boom_side"}),
        box(-1.5, 30, -94, 1.5, 58, -80, {"all": "hull", "left": "fin", "right": "fin"}),
        box(-15, 31, -88, 15, 33, -80, "hull"),
        box(-13, 0, -22, -11, 2, 34, "skid"), box(11, 0, -22, 13, 2, 34, "skid"),
        box(-13, 2, -14, -11, 8, -12, "skid"), box(11, 2, -14, 13, 8, -12, "skid"),
        box(-13, 2, 18, -11, 8, 20, "skid"), box(11, 2, 18, 13, 8, 20, "skid"),
    ])
    blades = [Part(f"blade_{i}", rotation=(0, 90 * i, 0), boxes=[box(-2.5, 0, 2, 2.5, 0.8, 76, "blade")]) for i in range(4)]
    rotor = Part("rotor", pivot=(0, 54, 0), boxes=[box(-3, -1, -3, 3, 2, 3, "metal")], children=blades)
    tail_blades = [Part(f"tail_blade_{i}", rotation=(90 * i, 0, 0), boxes=[box(0, 0, -1.2, 0.8, 14, 1.2, "blade")]) for i in range(4)]
    tail_rotor = Part("tail_rotor", pivot=(2, 50, -88), boxes=[box(0, -1.2, -1.2, 1.4, 1.2, 1.2, "metal")], children=tail_blades)
    return [body, rotor, tail_rotor]


HELI = {"hull": (84, 94, 82), "hull_dark": (52, 58, 52), "engine": (74, 84, 74), "metal": (60, 62, 64),
        "blade": (34, 36, 38), "skid": (44, 46, 46), "cockpit": (22, 30, 36)}


def heli_colour(spec, face, u, v, w, h, b):
    name, *args = spec if isinstance(spec, tuple) else (spec,)
    if name in ("hull_side", "boom_side", "fin", "nose", "engine_top"):
        c = HELI["hull" if name != "engine_top" else "engine"]
    else:
        c = HELI.get(name, (255, 0, 255))
    light = 1.12 if face == "top" else 0.6 if face == "bottom" else 1.0
    c = tuple(int(x * light) for x in c)
    seam = (int(u) % 12 == 0 and u > 1) or (int(v) % 10 == 0 and v > 1)
    if name in ("hull", "hull_side", "boom_side", "fin", "engine", "engine_top") and seam:
        c = tuple(int(x * 0.82) for x in c)
    if name == "hull_side":
        # A sliding door, windows along the cabin, the step.
        if 30 <= u < 52 and 4 <= v < 30 and (u in (30, 51) or v in (4, 29)):
            c = (30, 34, 30)
        if 34 <= u < 48 and 7 <= v < 15:
            c = (24, 32, 38)
        if (8 <= u < 20 or 60 <= u < 70) and 7 <= v < 14:
            c = (24, 32, 38)
    if name == "boom_side":
        # The Dutch roundel: red, white, blue, an orange heart.
        d = math.hypot(u - 29, v - 5)
        if d < 4.6:
            c = (230, 120, 20) if d < 1.6 else (30, 50, 140) if d < 2.6 else (230, 230, 226) if d < 3.6 else (180, 30, 30)
        if 8 <= u < 20 and v in (2, 3, 6, 7) and (u % 2 == 0):
            c = (200, 200, 190)
    if name == "fin" and 18 <= v < 24 and 3 <= u < 11:
        c = (230, 120, 20) if v < 20 else (230, 230, 226) if v < 22 else (30, 50, 140)
    if name == "nose" and v < 6:
        c = (24, 32, 38)
    if name == "cockpit":
        c = (60, 76, 88) if (u + v) % 7 < 2 else c
    if name == "blade" and (u < 1 or u > w - 1) and face == "top":
        c = (200, 200, 60)
    return c + (255,)


def helicopter():
    parts = helicopter_parts()
    width = 512
    height = mv.pack(parts, width)
    model = {"texture": [width, height], "tints": [], "parts": [p.to_json() for p in parts]}
    write(ROOT / "vehicles/helicopter.json", model)
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for part in parts:
        for b in part.all_boxes():
            for face, (x0, y0, fw, fh) in mv.face_regions(b).items():
                spec = b.face(face)
                for j in range(fh):
                    for i in range(fw):
                        if x0 + i < width and y0 + j < height:
                            img.putpixel((x0 + i, y0 + j), heli_colour(spec, face, i, j, fw, fh, b))
    out = ROOT / "textures/entity/helicopter.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)


def flare():
    """The flare particle: a hot red-white core in a red glow."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.2:
                c = (255, 236, 220, 255)
            elif d < 4.5:
                c = (255, 90, 60, 255)
            elif d < 7.5:
                a = int(200 * (7.5 - d) / 3.0)
                c = (230, 40, 30, a)
            else:
                continue
            img.putpixel((x, y), c)
    out = ROOT / "textures/particle/flare.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    write(ROOT / "particles/flare.json", {"textures": ["outbreak:flare"]})


def guts_overlay():
    """Walker guts smeared over a player: a 64x64 overlay in the player skin's layout, on both the
    inner and the outer layer (skins often have their clothes on the outer one): big blotches of dark
    blood and worse, thickest on the chest and the hands."""
    rng = random.Random("guts")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # (x0, y0, x1, y1, how covered): body, arms, legs, head; then the same on the outer layer.
    regions = [(16, 16, 40, 32, 0.6), (40, 16, 56, 32, 0.5), (32, 48, 48, 64, 0.5), (0, 16, 16, 32, 0.4), (16, 48, 32, 64, 0.4),
               (0, 0, 32, 16, 0.15),
               (16, 32, 40, 48, 0.6), (40, 32, 56, 48, 0.5), (48, 48, 64, 64, 0.5), (0, 32, 16, 48, 0.4), (0, 48, 16, 64, 0.4)]
    blobs = [(rng.uniform(0, 64), rng.uniform(0, 64), rng.uniform(2.0, 5.0)) for _ in range(200)]
    for x0, y0, x1, y1, cover in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                wet = max((r - math.hypot(x - bx, y - by)) / r for bx, by, r in blobs)
                if wet > 1.0 - cover - rng.random() * 0.25:
                    tone = rng.random()
                    c = (110, 16, 12) if tone < 0.45 else (66, 10, 10) if tone < 0.85 else (124, 100, 66)
                    img.putpixel((x, y), c + (255,))
    out = ROOT / "textures/entity/guts_overlay.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    write(ROOT / "models/item/walker_guts.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "outbreak:item/walker_guts"}})
    write(ROOT / "items/walker_guts.json", {"model": {"type": "minecraft:model", "model": "outbreak:item/walker_guts"}})


def rotor_loop():
    """A helicopter overhead: the blades' thwop, nine to the loop (so it loops cleanly), the turbine."""
    seconds = 2.0
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(5)
    noise = rng.normal(0, 1, n)
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    low = np.fft.irfft(spectrum * np.exp(-((freqs - 140) / 120) ** 2), n)
    low /= np.max(np.abs(low))
    beat = 0.5 + 0.5 * np.cos(2 * np.pi * 4.5 * t)
    slap = low * beat ** 6 * 1.4
    wind = np.fft.irfft(spectrum * np.exp(-((freqs - 900) / 900) ** 2), n)
    wind /= np.max(np.abs(wind))
    turbine = 0.05 * np.sin(2 * np.pi * 1180 * t) + 0.03 * np.sin(2 * np.pi * 2360 * t)
    return slap * 0.8 + wind * 0.18 + turbine


def gunshot_distant():
    """A gunshot a long way off: a dull crack, then its echo rolling round the buildings."""
    seconds = 2.2
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(11)
    noise = rng.normal(0, 1, n)
    crack = np.convolve(noise * np.exp(-t * 60), np.ones(25) / 25, mode="same") * 4
    tail = np.convolve(noise * np.exp(-t * 3.5), np.ones(140) / 140, mode="same") * 5
    echo = np.zeros(n)
    delay = int(0.32 * RATE)
    echo[delay:] = crack[:-delay] * 0.35
    return np.clip((crack + tail * 0.6 + echo), -1, 1) * envelope(n, 0.001, 0.4) * 0.9


def sounds_6b():
    save_ogg("helicopter", rotor_loop())
    save_ogg("gunshot_distant", gunshot_distant())
    events = {
        "helicopter": {"subtitle": "subtitles.outbreak.helicopter",
                       "sounds": [{"name": "outbreak:helicopter", "attenuation_distance": 160, "stream": True}]},
        "gunshot_distant": {"subtitle": "subtitles.outbreak.gunshot_distant",
                            "sounds": [{"name": "outbreak:gunshot_distant", "attenuation_distance": 220}]},
    }
    sounds_json = ROOT / "sounds.json"
    existing = json.loads(sounds_json.read_text())
    existing.update(events)
    write(sounds_json, existing)


def main():
    for name, art in SPRITES.items():
        sprite(name, art)
    dead_light()
    items()
    sounds()
    helicopter()
    flare()
    guts_overlay()
    sounds_6b()
    print("wrote the dead light, the radio, batteries, tap water, the helicopter, the flare, walker guts and the sounds")


if __name__ == "__main__":
    main()
