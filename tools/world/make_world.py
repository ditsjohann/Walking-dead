"""Generates the assets of the world winding down (phase 6): the dead light fixture the city's lamps
turn into when the power goes, the portable radio, batteries, a bottle of tap water, and the sounds
(the NL-Alert tone, radio static, a fuel pump running), synthesized and encoded with ffmpeg.

Run from the repo root:  python3 tools/world/make_world.py
"""
import json
import math
import random
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"
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


def main():
    for name, art in SPRITES.items():
        sprite(name, art)
    dead_light()
    items()
    sounds()
    print("wrote the dead light, the radio, batteries, tap water and the sounds")


if __name__ == "__main__":
    main()
