"""Procedural pixel art for the outbreak HUD and screens, in a few candidate styles.

Everything is painted at GUI-pixel resolution (1 texel = 1 GUI pixel, like vanilla) from code, so a
style can be tweaked and regenerated. `python3 tools/textures/hudart.py mockup <backdrop.png> <out_dir>`
paints a mockup of each style over a screenshot (taken at GUI scale 2);
`python3 tools/textures/hudart.py export` writes the game textures in the chosen style (DarkJournal).
"""
import os
import random
import sys
import tempfile
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[2]
MC_ASSETS = Path("/tmp/mc-assets")  # overridden by find_mc_assets()
OUR_TEXTURES = REPO / "src/main/resources/assets/outbreak/textures"


# --- helpers -----------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, d):
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d)) + tuple(c[3:])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def noise(img, amount, seed, mask=None):
    rnd = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            p = px[x, y]
            if p[3] == 0 or (mask and not mask(x, y)):
                continue
            d = rnd.randint(-amount, amount)
            px[x, y] = (clamp(p[0] + d), clamp(p[1] + d), clamp(p[2] + d), p[3])


def rect(img, x0, y0, x1, y1, color):
    """Fill [x0, x1) x [y0, y1)."""
    d = ImageDraw.Draw(img)
    if x1 > x0 and y1 > y0:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)


def frame(img, x0, y0, x1, y1, color):
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=color)


def bevel(img, x0, y0, x1, y1, light, dark):
    d = ImageDraw.Draw(img)
    d.line([x0, y0, x1 - 1, y0], fill=light)
    d.line([x0, y0, x0, y1 - 1], fill=light)
    d.line([x0, y1 - 1, x1 - 1, y1 - 1], fill=dark)
    d.line([x1 - 1, y0, x1 - 1, y1 - 1], fill=dark)


def blend(img, x, y, color, alpha):
    if 0 <= x < img.width and 0 <= y < img.height:
        p = img.getpixel((x, y))
        if p[3]:
            img.putpixel((x, y), mix(p[:3], color[:3], alpha) + (p[3],))


# --- Minecraft font ----------------------------------------------------------

class Font:
    def __init__(self, ascii_png):
        self.sheet = Image.open(ascii_png).convert("RGBA")
        self.widths = {}
        for code in range(256):
            gx, gy = (code % 16) * 8, (code // 16) * 8
            w = 0
            for x in range(8):
                if any(self.sheet.getpixel((gx + x, gy + y))[3] for y in range(8)):
                    w = x + 1
            self.widths[code] = w
        self.widths[32] = 3

    def width(self, text):
        return sum(self.widths.get(ord(c), 5) + 1 for c in text)

    def draw(self, img, text, x, y, color, shadow=True):
        if shadow:
            self._draw(img, text, x + 1, y + 1, (color[0] // 4, color[1] // 4, color[2] // 4, 255))
        self._draw(img, text, x, y, color)

    def _draw(self, img, text, x, y, color):
        for c in text:
            code = ord(c)
            if code < 256 and code != 32:
                gx, gy = (code % 16) * 8, (code // 16) * 8
                for dy in range(8):
                    for dx in range(self.widths[code]):
                        if self.sheet.getpixel((gx + dx, gy + dy))[3]:
                            if 0 <= x + dx < img.width and 0 <= y + dy < img.height:
                                img.putpixel((x + dx, y + dy), color[:3] + (255,))
            x += self.widths.get(code, 5) + 1


# --- styles --------------------------------------------------------------------

class Style:
    name = "base"
    text = (230, 225, 210, 255)
    dim = (150, 145, 135, 255)
    danger = (220, 70, 50, 255)
    shadow = True
    level_colors = [None, (200, 170, 60), (220, 120, 40), (200, 50, 40), (140, 10, 10)]

    def panel(self, w, h, seed=0):
        raise NotImplementedError

    def slot(self, selected=False):
        raise NotImplementedError

    def moodle(self, level):
        raise NotImplementedError

    def doll_colors(self):
        return (225, 220, 205, 255), (40, 35, 30, 255)


class ScavengedSteel(Style):
    """Riveted gunmetal plates, scratches, rust and hazard tape."""
    name = "A-scavenged-steel"
    base = (58, 60, 62, 255)

    def panel(self, w, h, seed=0, tape=True):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (w, h), self.base)
        # Brushed metal: faint horizontal streaks.
        for y in range(h):
            d = rnd.randint(-4, 4)
            for x in range(w):
                p = img.getpixel((x, y))
                img.putpixel((x, y), shade(p, d))
        noise(img, 5, seed)
        # Scratches.
        for _ in range(w * h // 180):
            x, y, ln = rnd.randrange(w), rnd.randrange(h), rnd.randint(3, 9)
            for i in range(ln):
                blend(img, x + i, y + (i // 3), (140, 140, 140), 0.45)
        # Rust blooms near the edges.
        for _ in range(3 + w // 40):
            cx = rnd.choice([rnd.randint(0, 6), rnd.randint(w - 7, w - 1), rnd.randrange(w)])
            cy = rnd.choice([rnd.randint(h - 6, h - 1), rnd.randint(0, 5)])
            for _ in range(25):
                blend(img, cx + rnd.randint(-3, 3), cy + rnd.randint(-2, 2), (120, 60, 25), rnd.uniform(0.2, 0.55))
        # Plate edge: dark outline, light top-left bevel, shadow bottom-right.
        frame(img, 0, 0, w, h, (16, 16, 17, 255))
        bevel(img, 1, 1, w - 1, h - 1, (110, 112, 114, 255), (30, 31, 32, 255))
        # Rivets.
        for rx, ry in [(3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)]:
            self._rivet(img, rx, ry)
        if tape and w > 60:
            self._hazard(img, w - 34, 0, 26, 3)
        return img

    def _rivet(self, img, x, y):
        rect(img, x, y, x + 2, y + 2, (150, 152, 155, 255))
        img.putpixel((x, y), (200, 200, 205, 255))
        img.putpixel((x + 1, y + 1), (70, 70, 72, 255))

    def _hazard(self, img, x0, y0, w, h):
        for y in range(h):
            for x in range(w):
                yellow = ((x + y) // 3) % 2 == 0
                img.putpixel((x0 + x, y0 + y), (225, 180, 30, 255) if yellow else (25, 25, 25, 255))

    def slot(self, selected=False):
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        rect(img, 1, 1, 19, 19, (28, 29, 30, 255))
        noise(img, 3, 7)
        bevel(img, 1, 1, 19, 19, (14, 14, 15, 255), (92, 94, 96, 255))  # recessed
        if selected:
            frame(img, 0, 0, 20, 20, (240, 175, 40, 255))
            frame(img, 1, 1, 19, 19, (160, 110, 20, 255))
        return img

    def moodle(self, level):
        img = self.panel(20, 20, seed=level, tape=False)
        c = self.level_colors[level]
        rect(img, 3, 3, 17, 17, c + (255,))
        noise(img, 6, level * 13, lambda x, y: 3 <= x < 17 and 3 <= y < 17)
        bevel(img, 3, 3, 17, 17, shade(c + (255,), 40), shade(c + (255,), -50))
        return img


class FieldKit(Style):
    """Olive canvas, stitched seams, leather edging and brass."""
    name = "B-field-kit"
    canvas = (84, 90, 56, 255)
    text = (238, 228, 196, 255)
    dim = (176, 170, 140, 255)

    def panel(self, w, h, seed=0):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (w, h), self.canvas)
        # Woven canvas: alternating threads.
        px = img.load()
        for y in range(h):
            for x in range(w):
                d = (4 if (x + (y // 2)) % 2 == 0 else -4) + (3 if y % 2 == 0 else -2)
                px[x, y] = shade(self.canvas, d + rnd.randint(-3, 3))
        # Wear: a few lighter faded patches.
        for _ in range(w * h // 300):
            cx, cy = rnd.randrange(w), rnd.randrange(h)
            for _ in range(12):
                blend(img, cx + rnd.randint(-2, 2), cy + rnd.randint(-1, 1), (150, 150, 110), 0.25)
        # Leather edging.
        frame(img, 0, 0, w, h, (52, 36, 22, 255))
        frame(img, 1, 1, w - 1, h - 1, (92, 64, 40, 255))
        # Stitches.
        thread = (214, 200, 150, 255)
        for x in range(4, w - 4, 4):
            rect(img, x, 3, x + 2, 4, thread)
            rect(img, x, h - 4, x + 2, h - 3, thread)
        for y in range(4, h - 4, 4):
            rect(img, 3, y, 4, y + 2, thread)
            rect(img, w - 4, y, w - 3, y + 2, thread)
        return img

    def slot(self, selected=False):
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        rect(img, 1, 1, 19, 19, (58, 62, 38, 255))
        noise(img, 4, 3)
        frame(img, 1, 1, 19, 19, (40, 42, 26, 255))
        for i in range(3, 17, 3):
            img.putpixel((i, 2), (150, 140, 100, 255))
            img.putpixel((i, 17), (150, 140, 100, 255))
        if selected:
            frame(img, 0, 0, 20, 20, (214, 170, 70, 255))
            rect(img, 8, 0, 12, 2, (240, 205, 110, 255))  # buckle
            frame(img, 1, 1, 19, 19, (150, 110, 40, 255))
        return img

    def moodle(self, level):
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        c = self.level_colors[level]
        d.ellipse([0, 0, 19, 19], fill=(52, 36, 22, 255))
        d.ellipse([1, 1, 18, 18], fill=c + (255,))
        noise(img, 6, level, lambda x, y: (x - 9.5) ** 2 + (y - 9.5) ** 2 < 70)
        for a in range(0, 360, 30):
            import math
            x = int(9.5 + 7.5 * math.cos(math.radians(a)))
            y = int(9.5 + 7.5 * math.sin(math.radians(a)))
            img.putpixel((x, y), (235, 220, 170, 255))
        return img


class Journal(Style):
    """Aged paper, masking tape, pencil and red marker."""
    name = "C-survivors-journal"
    paper = (214, 201, 168, 255)
    text = (52, 44, 36, 255)
    dim = (110, 98, 82, 255)
    danger = (170, 28, 28, 255)
    shadow = False
    pencil = (90, 84, 78, 255)
    tape = (232, 222, 176, 230)
    edge_width = 4
    edge_alpha = 0.08
    grime_spots = 0

    def panel(self, w, h, seed=0):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (w, h), self.paper)
        noise(img, 7, seed)
        # Fibres and grime towards the edges.
        for y in range(h):
            for x in range(w):
                edge = min(x, y, w - 1 - x, h - 1 - y)
                if edge < self.edge_width:
                    blend(img, x, y, (90, 68, 40), (self.edge_width - edge) * self.edge_alpha)
        # Dirty thumbprints and smudges.
        for _ in range(self.grime_spots * max(1, w * h // 2000)):
            cx, cy = rnd.randrange(w), rnd.randrange(h)
            for _ in range(30):
                blend(img, cx + rnd.randint(-3, 3), cy + rnd.randint(-2, 2), (70, 52, 32), rnd.uniform(0.05, 0.2))
        for _ in range(2):
            cx, cy, r = rnd.randrange(w), rnd.randrange(h), rnd.randint(4, 8)
            for a in range(0, 360, 6):
                import math
                x = int(cx + r * math.cos(math.radians(a)))
                y = int(cy + r * math.sin(math.radians(a)))
                blend(img, x, y, (140, 100, 50), 0.35)  # coffee ring
        # Torn edges.
        px = img.load()
        for x in range(w):
            for y in range(rnd.choice([0, 0, 1, 1, 2])):
                px[x, y] = (0, 0, 0, 0)
            for y in range(rnd.choice([0, 0, 1, 2])):
                px[x, h - 1 - y] = (0, 0, 0, 0)
        # Masking tape at the top corners.
        for tx in (2, w - 14):
            for y in range(0, 6):
                for x in range(tx, tx + 12):
                    if 0 <= x < w:
                        px[x, y] = self.tape
            noise(img, 4, tx, lambda x, y, tx=tx: tx <= x < tx + 12 and y < 6)
        return img

    def slot(self, selected=False):
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        rnd = random.Random(5)
        pencil = self.pencil
        for i in range(1, 19):
            img.putpixel((i, 1 + (rnd.random() < 0.2)), pencil)
            img.putpixel((i, 18 - (rnd.random() < 0.2)), pencil)
            img.putpixel((1 + (rnd.random() < 0.2), i), pencil)
            img.putpixel((18 - (rnd.random() < 0.2), i), pencil)
        if selected:
            d = ImageDraw.Draw(img)
            d.ellipse([0, 0, 19, 19], outline=(185, 30, 30, 255), width=2)
        return img

    def moodle(self, level):
        img = self.panel(20, 20, seed=level + 40)
        c = self.level_colors[level]
        rect(img, 2, 15, 18, 18, c + (255,))  # marker underline
        return img

    def doll_colors(self):
        return (196, 184, 152, 255), (70, 62, 54, 255)


class DarkJournal(Journal):
    """The chosen style: the old paper, but older and darker: grimy, stained, handled a lot."""
    name = "C-dark-paper"
    paper = (166, 148, 110, 255)
    text = (26, 20, 14, 255)
    dim = (66, 54, 40, 255)
    danger = (140, 16, 14, 255)
    pencil = (52, 46, 40, 255)
    tape = (196, 182, 138, 235)
    edge_width = 6
    edge_alpha = 0.11
    grime_spots = 2

    def doll_colors(self):
        return (148, 134, 102, 255), (38, 32, 26, 255)


class Notebook(Journal):
    """Torn-out notebook page: ruled lines, red margin, blue ballpoint, highlighter, sticky notes."""
    name = "C1-notebook"
    paper = (238, 234, 222, 255)
    text = (34, 52, 120, 255)
    dim = (96, 110, 150, 255)
    danger = (190, 30, 40, 255)

    def panel(self, w, h, seed=0):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (w, h), self.paper)
        noise(img, 4, seed)
        for y in range(5, h, 7):
            for x in range(w):
                blend(img, x, y, (150, 180, 225), 0.55)
        if w > 50:
            for y in range(h):
                blend(img, 10, y, (220, 90, 90), 0.6)
        if h > 40:
            for hy in range(8, h - 6, 20):
                d = ImageDraw.Draw(img)
                d.ellipse([2, hy, 6, hy + 4], fill=(0, 0, 0, 0))
        # Perforated top edge where it was torn out.
        px = img.load()
        for x in range(w):
            if x % 3 == 0 or rnd.random() < 0.25:
                px[x, 0] = (0, 0, 0, 0)
        for y in range(h):
            for x in range(w):
                edge = min(x, y, w - 1 - x, h - 1 - y)
                if edge < 2 and px[x, y][3]:
                    blend(img, x, y, (160, 150, 130), 0.25)
        return img

    def slot(self, selected=False):
        img = super().slot(False)
        if selected:
            hl = Image.new("RGBA", (20, 20), (250, 235, 60, 110))
            hl.alpha_composite(img)
            img = hl
            frame(img, 1, 1, 19, 19, (34, 52, 120, 255))
        return img

    def moodle(self, level):
        colors = [None, (250, 236, 120), (250, 190, 90), (240, 120, 110), (215, 50, 50)]
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        rect(img, 2, 2, 20, 20, (0, 0, 0, 70))  # shadow
        rect(img, 0, 0, 18, 18, colors[level] + (255,))
        noise(img, 5, level + 70, lambda x, y: x < 18 and y < 18)
        rect(img, 0, 0, 18, 3, shade(colors[level] + (255,), -18))  # sticky strip
        return img


class Cardboard(Journal):
    """Corrugated cardboard, silver duct tape and black marker."""
    name = "C2-cardboard"
    board = (176, 136, 90, 255)
    text = (24, 20, 18, 255)
    dim = (78, 58, 40, 255)
    danger = (180, 20, 20, 255)

    def panel(self, w, h, seed=0):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (w, h), self.board)
        px = img.load()
        for y in range(h):
            for x in range(w):
                flute = 6 if x % 4 == 0 else (-4 if x % 4 == 2 else 0)
                px[x, y] = shade(self.board, flute + rnd.randint(-4, 4))
        # Torn edges show the lighter inner layer.
        for x in range(w):
            for y in range(rnd.choice([0, 1, 1, 2])):
                px[x, y] = (214, 184, 140, 255) if y else (0, 0, 0, 0)
            for y in range(rnd.choice([0, 1, 2])):
                px[x, h - 1 - y] = (214, 184, 140, 255) if y else (0, 0, 0, 0)
        frame(img, 0, 0, w, h, (110, 80, 50, 255))
        # Duct tape across two corners.
        for tx, ty in ((0, 2), (w - 16, h - 9)):
            for y in range(ty, min(h, ty + 7)):
                for x in range(max(0, tx), min(w, tx + 16)):
                    weave = 6 if (x + y) % 2 == 0 else -6
                    px[x, y] = shade((168, 170, 174, 245), weave + rnd.randint(-5, 5))
        return img

    def slot(self, selected=False):
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        rnd = random.Random(9)
        ink = (30, 26, 24, 255)
        for i in range(1, 19):
            for p in ((i, 1), (i, 18), (1, i), (18, i)):
                img.putpixel(p, ink)
            if rnd.random() < 0.3:
                img.putpixel((i, 2), ink)
        if selected:
            frame(img, 0, 0, 20, 20, (190, 25, 25, 255))
            frame(img, 1, 1, 19, 19, (190, 25, 25, 255))
        return img

    def moodle(self, level):
        img = self.panel(20, 20, seed=level + 90)
        c = self.level_colors[level]
        for x in range(2, 18):
            img.putpixel((x, 16 + (x % 3 == 0)), c + (255,))
            img.putpixel((x, 17), c + (255,))
        return img


class FieldNotes(Journal):
    """Folded graph paper, pencil, a paperclip and coffee."""
    name = "C3-field-notes"
    paper = (224, 226, 210, 255)
    text = (58, 58, 64, 255)
    dim = (110, 112, 112, 255)
    danger = (175, 35, 30, 255)

    def panel(self, w, h, seed=0):
        rnd = random.Random(seed)
        img = Image.new("RGBA", (w, h), self.paper)
        noise(img, 4, seed)
        for y in range(h):
            for x in range(w):
                if x % 5 == 0 or y % 5 == 0:
                    blend(img, x, y, (150, 175, 150), 0.35)
        # Fold creases.
        if w > 60:
            for y in range(h):
                blend(img, w // 2, y, (120, 110, 90), 0.3)
                blend(img, w // 2 + 1, y, (255, 255, 255), 0.3)
        # Coffee drip.
        cx, cy = rnd.randrange(w), rnd.randrange(h)
        for _ in range(40):
            blend(img, cx + rnd.randint(-4, 4), cy + rnd.randint(-3, 3), (150, 105, 60), 0.18)
        frame(img, 0, 0, w, h, (170, 168, 150, 255))
        # Paperclip on the top-left corner.
        if w > 30:
            clip = (150, 155, 165, 255)
            frame(img, 4, -2, 9, 8, clip)
            frame(img, 5, -2, 8, 6, (110, 115, 125, 255))
        return img

    def slot(self, selected=False):
        img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
        frame(img, 1, 1, 19, 19, (70, 70, 76, 255))
        if selected:
            d = ImageDraw.Draw(img)
            d.ellipse([0, 0, 19, 19], outline=(175, 35, 30, 255), width=2)
        return img

    def moodle(self, level):
        img = Image.new("RGBA", (20, 20), (245, 243, 232, 255))
        noise(img, 3, level)
        c = self.level_colors[level]
        rect(img, 0, 0, 20, 3, c + (255,))
        frame(img, 0, 0, 20, 20, (150, 150, 140, 255))
        return img


STYLES = [ScavengedSteel(), FieldKit(), Journal()]
PAPER_VARIANTS = [Notebook(), Cardboard(), FieldNotes()]
CHOSEN = DarkJournal()


# --- mockup ------------------------------------------------------------------

def find_mc_assets():
    """Vanilla's textures (for item icons and the font in mockups): $MC_ASSETS, or unpacked from Loom's client jar."""
    if "MC_ASSETS" in os.environ:
        return Path(os.environ["MC_ASSETS"])
    out = Path(tempfile.gettempdir()) / "outbreak-mc-assets"
    if not (out / "assets/minecraft/textures").is_dir():
        jars = sorted(Path.home().glob(".gradle/caches/fabric-loom/*/minecraft-client.jar"))
        if not jars:
            raise SystemExit("Minecraft client jar not found: run ./gradlew build once, or set MC_ASSETS")
        with zipfile.ZipFile(jars[-1]) as jar:
            jar.extractall(out, [n for n in jar.namelist() if n.startswith("assets/minecraft/textures/")])
    return out / "assets/minecraft"


def item(name):
    if name.startswith("outbreak:"):
        return Image.open(OUR_TEXTURES / "item" / f"{name[9:]}.png").convert("RGBA")
    return Image.open(MC_ASSETS / "textures/item" / f"{name}.png").convert("RGBA")


def doll(style, wounds):
    """The body doll: wounds is {part: rgb}."""
    body, line = style.doll_colors()
    img = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    parts = {"head": (4, 0, 8, 8), "torso": (4, 8, 8, 12), "right_arm": (0, 8, 4, 12),
             "left_arm": (12, 8, 4, 12), "right_leg": (4, 20, 4, 12), "left_leg": (8, 20, 4, 12)}
    for part, (x, y, w, h) in parts.items():
        rect(img, x, y, x + w, y + h, line)
        rect(img, x + 1, y + 1, x + w - 1, y + h - 1, wounds.get(part, body))
    return img.resize((32, 64), Image.NEAREST)


def mockup(style, backdrop, font, out):
    W, H = 427, 240
    hud = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # Hotbar.
    items = ["outbreak:baseball_bat", "outbreak:kitchen_knife", "outbreak:hammer", "outbreak:bandage", "outbreak:rag",
             "bread", "outbreak:boiled_water", None, None]
    bar = style.panel(9 * 20 + 8, 28, seed=11)
    for i, it in enumerate(items):
        s = style.slot(selected=(i == 0))
        bar.alpha_composite(s, (4 + i * 20, 4))
        if it:
            bar.alpha_composite(item(it), (6 + i * 20, 6))
    bar_x = max((W - bar.width) // 2, 134)
    hud.alpha_composite(bar, (bar_x, H - bar.height - 2))

    # Item name.
    name = "Baseball Bat  70%"
    tag = style.panel(font.width(name) + 12, 15, seed=3)
    font.draw(tag, name, 6, 4, style.text, style.shadow)
    hud.alpha_composite(tag, (bar_x + (bar.width - tag.width) // 2, H - bar.height - 18))

    # Status panel.
    st = style.panel(124, 76, seed=21)
    st.alpha_composite(doll(style, {"left_arm": (220, 120, 40, 255), "torso": (232, 197, 71, 255)}), (6, 6))
    tx = 44
    font.draw(st, "Day 3", tx, 8, style.text, style.shadow)
    font.draw(st, "19:40", tx + 42, 8, style.dim, style.shadow)
    font.draw(st, "Health 82%", tx, 21, style.text, style.shadow)
    font.draw(st, "Quiet", tx, 34, style.text, style.shadow)
    for i in range(3):
        bh = 3 + i * 3
        col = (220, 175, 60, 255) if i < 1 else (120, 115, 105, 160)
        rect(st, tx + 50 + i * 5, 42 - bh, tx + 53 + i * 5, 42, col)
    font.draw(st, "9.4 / 12 kg", tx, 47, style.dim, style.shadow)
    font.draw(st, "Diepenring", tx, 60, style.dim, style.shadow)
    hud.alpha_composite(st, (3, H - st.height - 3))

    # Moodles.
    for i, (mood, level) in enumerate([("thirsty", 1), ("panic", 3), ("bleeding", 2), ("pain", 1)]):
        tile = style.moodle(level)
        icon = Image.open(OUR_TEXTURES / "gui/sprites/moodle" / f"{mood}.png").convert("RGBA")
        tile.alpha_composite(icon, (2, 2))
        hud.alpha_composite(tile, (W - 24, 4 + i * 22))

    # Crosshair, lined up on a head.
    cx, cy = W // 2, H // 2
    red = (220, 50, 40, 255)
    rect(hud, cx - 2, cy - 2, cx + 2, cy + 2, (0, 0, 0, 140))
    rect(hud, cx - 1, cy - 1, cx + 1, cy + 1, red)
    for sx, sy in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
        rect(hud, cx + sx * 6 - (3 if sx < 0 else 0), cy + sy * 6 - (1 if sy < 0 else 0), cx + sx * 6 + (0 if sx < 0 else 3), cy + sy * 6 + (0 if sy < 0 else 1), red)

    # Style name.
    font.draw(hud, style.name, 4, 4, (255, 255, 255, 255))

    frame_img = backdrop.copy().convert("RGBA")
    frame_img.alpha_composite(hud.resize((W * 2, H * 2), Image.NEAREST))
    frame_img.save(out)
    print("wrote", out)


# --- export: the real game textures in the chosen style ---------------------------------------

RES = REPO / "src/main/resources/assets"


def save(img, rel, namespace="outbreak"):
    out = RES / namespace / "textures" / rel
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print("wrote", out.relative_to(REPO))


def inset_slot(img, x, y, base):
    """A recessed 18x18 slot cut into a material."""
    inner = shade(base, -45)
    rect(img, x, y, x + 18, y + 18, inner)
    noise(img, 3, x * 31 + y, lambda px, py: x <= px < x + 18 and y <= py < y + 18)
    bevel(img, x, y, x + 18, y + 18, shade(base, -80), shade(base, 25))


def pencil_box(img, x, y, color, seed=0):
    rnd = random.Random(seed)
    for i in range(18):
        for p in ((x + i, y + (rnd.random() < 0.15)), (x + i, y + 17 - (rnd.random() < 0.15)),
                  (x + (rnd.random() < 0.15), y + i), (x + 17 - (rnd.random() < 0.15), y + i)):
            if 0 <= p[0] < img.width and 0 <= p[1] < img.height:
                img.putpixel(p, color)


# Materials: each returns an RGBA image of the given size and the base colour slots are cut from.

def mat_denim(w, h, seed):
    base = (52, 72, 120, 255)
    img = Image.new("RGBA", (w, h), base)
    px = img.load()
    rnd = random.Random(seed)
    for y in range(h):
        for x in range(w):
            d = (12 if (x + y) % 3 == 0 else -6 if (x + y) % 3 == 1 else 0) + rnd.randint(-4, 4)
            px[x, y] = shade(base, d)
    frame(img, 0, 0, w, h, (28, 38, 66, 255))
    for x in range(4, w - 4, 3):  # orange jeans stitching
        rect(img, x, 4, x + 2, 5, (214, 140, 56, 255))
        rect(img, x, h - 5, x + 2, h - 4, (214, 140, 56, 255))
    for x in range(2, w - 2):  # zipper along the top
        px[x, 1] = (176, 176, 182, 255) if x % 2 else (100, 100, 106, 255)
        px[x, 2] = (100, 100, 106, 255) if x % 2 else (176, 176, 182, 255)
    rect(img, w - 14, 0, w - 9, 5, (190, 190, 196, 255))  # zip pull
    return img, base


def mat_canvas(w, h, seed):
    img = FieldKit().panel(w, h, seed)
    base = FieldKit.canvas
    for sx in (w // 4, 3 * w // 4):  # straps with brass buckles
        rect(img, sx - 3, 0, sx + 3, h, (74, 58, 38, 255))
        noise(img, 4, sx, lambda x, y, sx=sx: sx - 3 <= x < sx + 3)
        frame(img, sx - 4, h // 2 - 3, sx + 4, h // 2 + 3, (200, 160, 70, 255))
    return img, base


def mat_wood(w, h, seed, base=(118, 78, 44, 255), plank=6, doors=False, nails=False, vertical=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), base)
    px = img.load()
    import math
    span = w if vertical else h
    for i in range(0, span, plank):
        tone = rnd.randint(-14, 14)
        phase = rnd.random() * 6
        for a in range(i, min(span, i + plank)):
            for b in range(w if not vertical else h):
                x, y = (a, b) if vertical else (b, a)
                grain = int(6 * math.sin((b + phase * 10) / 7.0 + (a - i) * 0.9))
                px[x, y] = shade(base, tone + grain + rnd.randint(-3, 3))
        for b in range(w if not vertical else h):  # gap between planks
            x, y = (i, b) if vertical else (b, i)
            if 0 <= x < w and 0 <= y < h:
                px[x, y] = shade(base, -60)
        if nails:
            for b in (3, (w if not vertical else h) - 4):
                x, y = (i + plank // 2, b) if vertical else (b, i + plank // 2)
                if 0 <= x < w and 0 <= y < h:
                    px[x, y] = (150, 150, 155, 255)
    frame(img, 0, 0, w, h, shade(base, -70))
    bevel(img, 1, 1, w - 1, h - 1, shade(base, 30), shade(base, -45))
    if doors:
        rect(img, w // 2, 1, w // 2 + 1, h - 1, shade(base, -70))
        rect(img, w // 2 - 3, h // 2 - 4, w // 2 - 2, h // 2 + 4, (190, 170, 120, 255))
        rect(img, w // 2 + 3, h // 2 - 4, w // 2 + 4, h // 2 + 4, (190, 170, 120, 255))
    return img, base


def mat_enamel(w, h, seed, rows):
    base = (226, 230, 228, 255)
    img = Image.new("RGBA", (w, h), base)
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = shade((222, 230, 236, 255), int(8 - 14 * y / h))
    noise(img, 2, seed)
    # Rubber door seal.
    frame(img, 0, 0, w, h, (120, 124, 126, 255))
    frame(img, 1, 1, w - 1, h - 1, (160, 164, 166, 255))
    # Wire shelves under each row.
    for r in range(1, rows):
        y = 3 + r * 18
        for x in range(3, w - 3):
            px[x, y] = (178, 184, 188, 255)
        for x in range(3, w - 3, 4):
            px[x, y - 1] = (196, 200, 204, 255)
    return img, (210, 216, 220, 255)


def mat_painted(w, h, seed):
    rnd = random.Random(seed)
    paint = (150, 188, 168, 255)
    img, _ = mat_wood(w, h, seed, base=(128, 88, 50, 255), plank=8, vertical=True)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if rnd.random() > 0.06:  # paint, chipped here and there
                px[x, y] = shade(paint, rnd.randint(-5, 5))
    frame(img, 0, 0, w, h, (70, 96, 84, 255))
    frame(img, 3, 3, w - 3, h - 3, (118, 154, 136, 255))
    return img, paint


def mat_red_steel(w, h, seed):
    rnd = random.Random(seed)
    base = (164, 36, 30, 255)
    img = Image.new("RGBA", (w, h), base)
    noise(img, 6, seed)
    for _ in range(w * h // 120):
        x, y, ln = rnd.randrange(w), rnd.randrange(h), rnd.randint(2, 6)
        for i in range(ln):
            blend(img, x + i, y, (210, 170, 160), 0.4)
    frame(img, 0, 0, w, h, (70, 14, 12, 255))
    bevel(img, 1, 1, w - 1, h - 1, (210, 80, 70, 255), (100, 20, 16, 255))
    for lx in (w // 4, 3 * w // 4):  # latches
        rect(img, lx - 3, 0, lx + 3, 4, (170, 172, 176, 255))
        rect(img, lx - 1, 1, lx + 1, 3, (90, 90, 95, 255))
    return img, base


def mat_gray_steel(w, h, seed, rows):
    img = ScavengedSteel().panel(w, h, seed, tape=False)
    px = img.load()
    for y in range(h):  # cabinet steel is lighter than the HUD plates
        for x in range(w):
            px[x, y] = shade(px[x, y], 34)
    for r in range(1, rows):  # drawer seams
        y = 3 + r * 18
        for x in range(2, w - 2):
            px[x, y] = (22, 22, 24, 255)
            if y + 1 < h:
                px[x, y + 1] = (96, 98, 100, 255)
    return img, shade(ScavengedSteel.base, 34)


# Container screen layout: must match ContainerKind.java.
KINDS = {
    "school_bag": (9, 2), "hiking_bag": (9, 3), "cupboard": (9, 3), "wardrobe": (9, 6), "crate": (9, 3),
    "fridge": (6, 3), "kitchen_cupboard": (6, 2), "toolbox": (5, 2), "metal_cabinet": (9, 3),
}


def material(kind, w, h, rows):
    seed = sum(map(ord, kind))
    return {
        "school_bag": lambda: mat_denim(w, h, seed),
        "hiking_bag": lambda: mat_canvas(w, h, seed),
        "cupboard": lambda: mat_wood(w, h, seed),
        "wardrobe": lambda: mat_wood(w, h, seed, base=(126, 84, 48, 255), doors=True),
        "crate": lambda: mat_wood(w, h, seed, base=(164, 124, 78, 255), plank=9, nails=True, vertical=True),
        "fridge": lambda: mat_enamel(w, h, seed, rows),
        "kitchen_cupboard": lambda: mat_painted(w, h, seed),
        "toolbox": lambda: mat_red_steel(w, h, seed),
        "metal_cabinet": lambda: mat_gray_steel(w, h, seed, rows),
    }[kind]()


def container_screen(style, kind):
    cols, rows = KINDS[kind]
    left = 88 - cols * 9 + 1
    top = 18
    pockets = top + rows * 18 + 16
    hotbar = pockets + 22
    width, height = 176, hotbar + 26
    page = style.panel(width, height, seed=sum(map(ord, kind)) + 1)
    # The container itself: its own material, with a shadow on the paper.
    x0, y0 = left - 5, top - 3
    x1, y1 = left + cols * 18 + 4, top + rows * 18 + 4
    shadow = Image.new("RGBA", (x1 - x0, y1 - y0), (30, 20, 10, 90))
    page.alpha_composite(shadow, (x0 + 2, y0 + 2))
    mat, base = material(kind, x1 - x0, y1 - y0, rows)
    for r in range(rows):
        for c in range(cols):
            inset_slot(mat, left - 1 + c * 18 - x0, top - 1 + r * 18 - y0, base)
    page.alpha_composite(mat, (x0, y0))
    # Your pockets and hotbar, pencilled on the paper.
    for i in range(9):
        pencil_box(page, 7 + i * 18, pockets - 1, style.pencil, seed=i)
        pencil_box(page, 7 + i * 18, hotbar - 1, style.pencil, seed=i + 9)
    for x in range(8, 168, 2):
        page.putpixel((x, hotbar - 4), shade(style.pencil, 60))
    save(page, f"gui/container/{kind}.png")


def inventory_page(style):
    """Replaces vanilla's inventory texture: same layout, as a page of notes."""
    page = style.panel(176, 166, seed=77)
    pencil = style.pencil
    # A polaroid of yourself where the player model stands.
    rect(page, 24, 6, 76, 82, (222, 216, 200, 255))
    rect(page, 26, 8, 74, 76, (44, 40, 36, 255))
    for y in range(8, 76):
        for x in range(26, 74):
            blend(page, x, y, (90, 80, 70), (76 - y) / 200)
    noise(page, 3, 5, lambda x, y: 24 <= x < 76 and 6 <= y < 82)
    for y in range(4):  # tape holding the photo
        for x in range(40, 60):
            page.putpixel((x, 4 + y), style.tape)
    for y in (7, 25, 43, 61):  # equipment
        pencil_box(page, 7, y, pencil, seed=y)
    pencil_box(page, 76, 61, pencil, seed=3)  # offhand
    for (x, y) in ((97, 17), (115, 17), (97, 35), (115, 35)):  # crafting
        pencil_box(page, x, y, pencil, seed=x + y)
    for x in range(136, 150):  # arrow
        page.putpixel((x, 36), pencil)
    for d in range(3):
        page.putpixel((149 - d, 36 - d), pencil)
        page.putpixel((149 - d, 36 + d), pencil)
    d = ImageDraw.Draw(page)
    d.rectangle([152, 26, 171, 45], outline=style.danger)  # result in red marker
    d.rectangle([153, 27, 170, 44], outline=style.danger)
    for i in range(9):
        pencil_box(page, 7 + i * 18, 83, pencil, seed=i)
        pencil_box(page, 7 + i * 18, 141, pencil, seed=i + 9)
    sheet = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    sheet.alpha_composite(page, (0, 0))
    save(sheet, "gui/container/inventory.png", namespace="minecraft")


def block_textures():
    def face(fill, w=16, h=16):
        img = Image.new("RGBA", (w, h), fill)
        return img

    # Fridge: white enamel, freezer door on top, long handle.
    side = face((224, 228, 228, 255))
    noise(side, 3, 1)
    bevel(side, 0, 0, 16, 16, (240, 244, 244, 255), (170, 176, 178, 255))
    front = side.copy()
    rect(front, 1, 5, 15, 6, (150, 156, 158, 255))
    rect(front, 12, 1, 14, 4, (130, 134, 138, 255))
    rect(front, 12, 7, 14, 14, (130, 134, 138, 255))
    top = face((232, 236, 236, 255))
    noise(top, 2, 2)
    frame(top, 0, 0, 16, 16, (180, 186, 188, 255))
    for name, img in (("fridge_side", side), ("fridge_front", front), ("fridge_top", top)):
        save(img, f"block/{name}.png")

    # Kitchen cupboard: painted doors under a stone counter top.
    paint, _ = mat_painted(16, 16, 9)
    front = paint.copy()
    rect(front, 7, 1, 9, 15, (70, 96, 84, 255))
    rect(front, 5, 6, 6, 9, (200, 196, 180, 255))
    rect(front, 10, 6, 11, 9, (200, 196, 180, 255))
    counter = face((120, 118, 114, 255))
    noise(counter, 10, 4)
    frame(counter, 0, 0, 16, 16, (90, 88, 86, 255))
    for name, img in (("kitchen_cupboard_side", paint), ("kitchen_cupboard_front", front), ("kitchen_cupboard_top", counter)):
        save(img, f"block/{name}.png")

    # Toolbox: red steel with latches.
    red, _ = mat_red_steel(16, 16, 3)
    top = face((150, 32, 28, 255))
    noise(top, 6, 5)
    frame(top, 0, 0, 16, 16, (80, 16, 14, 255))
    save(red, "block/toolbox.png")
    save(top, "block/toolbox_top.png")
    handle = face((60, 60, 64, 255))
    noise(handle, 6, 6)
    save(handle, "block/toolbox_handle.png")

    # Metal cabinet: grey steel with three drawers.
    steel = face(ScavengedSteel.base)
    noise(steel, 5, 7)
    bevel(steel, 0, 0, 16, 16, (110, 112, 114, 255), (30, 31, 32, 255))
    front = steel.copy()
    for y in (5, 10):
        rect(front, 1, y, 15, y + 1, (22, 22, 24, 255))
    for y in (2, 7, 12):
        rect(front, 6, y, 10, y + 1, (170, 172, 176, 255))
    for name, img in (("metal_cabinet_side", steel), ("metal_cabinet_front", front), ("metal_cabinet_top", steel)):
        save(img, f"block/{name}.png")


def hud_sprites(style):
    hotbar = style.panel(188, 28, seed=11)
    for i in range(9):
        hotbar.alpha_composite(style.slot(False), (4 + i * 20, 4))
    save(hotbar, "gui/sprites/hud/hotbar.png")
    sel = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(sel)
    d.ellipse([0, 0, 19, 19], outline=style.danger, width=2)
    save(sel, "gui/sprites/hud/hotbar_selection.png")
    offhand = style.panel(28, 28, seed=12)
    offhand.alpha_composite(style.slot(False), (4, 4))
    save(offhand, "gui/sprites/hud/offhand.png")
    save(style.panel(124, 76, seed=21), "gui/sprites/hud/status.png")
    # The item-name tag stretches to fit (nine-slice), so no tape on it.
    tag = style.panel(48, 15, seed=3)
    px = tag.load()
    for y in range(6):
        for x in range(48):
            if px[x, y][:3] == style.tape[:3]:
                px[x, y] = shade(style.paper, 0)
    save(tag, "gui/sprites/hud/tag.png")
    meta = RES / "outbreak/textures/gui/sprites/hud/tag.png.mcmeta"
    meta.write_text('{\n\t"gui": {\n\t\t"scaling": {\n\t\t\t"type": "nine_slice",\n\t\t\t"width": 48,\n\t\t\t"height": 15,\n\t\t\t"border": 5\n\t\t}\n\t}\n}\n')
    for level in range(1, 5):
        save(style.moodle(level), f"gui/sprites/hud/moodle_{level}.png")
    save(style.panel(330, 210, seed=31), "gui/health_panel.png")


def export(style):
    for kind in KINDS:
        container_screen(style, kind)
    inventory_page(style)
    block_textures()
    hud_sprites(style)


def main():
    global MC_ASSETS
    if sys.argv[1] == "export":
        export(CHOSEN)
        return

    MC_ASSETS = find_mc_assets()
    font = Font(MC_ASSETS / "textures/font/ascii.png")
    if sys.argv[1] == "mockup":
        backdrop = Image.open(sys.argv[2])
        out = Path(sys.argv[3])
        out.mkdir(parents=True, exist_ok=True)
        which = sys.argv[4] if len(sys.argv) > 4 else ""
        styles = PAPER_VARIANTS if which == "paper" else [CHOSEN] if which == "chosen" else STYLES
        for style in styles:
            mockup(style, backdrop, font, out / f"{style.name}.png")


if __name__ == "__main__":
    main()
