"""Generates the vehicle models and their textures.

Run from the repo root:  python3 tools/vehicles/make_vehicles.py

A model is a tree of parts made of boxes, in pixels (16 = 1 block), with x to the vehicle's right,
y up and z forward. It's written as JSON (assets/outbreak/vehicles/<type>.json) that VehicleModel.java
turns into a Minecraft model, and every box gets its own patch of the texture (Minecraft's box UV
layout), painted per face: one texture per colour, plus a burnt-out "wreck" one.

Part names the game looks for: "wheel_<n>" (spins; hidden without a tire; n is the wheel slot:
0 front left, 1 front right, 2 rear left, 3 rear right; bikes 0 front, 1 rear), "steer" (turns with
the steering) and anything starting with "glass" (hidden once the windows are broken).
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"

COLORS = {
    "red": (168, 32, 28), "blue": (36, 70, 140), "white": (222, 222, 216), "silver": (160, 164, 168),
    "black": (34, 34, 38), "green": (46, 96, 58), "cream": (226, 214, 176),
}
# Must match VehicleType.java (colour index = position in the list).
VARIANTS = {
    "bicycle": ["black", "red", "blue", "green"],
    "scooter": ["red", "white", "black", "cream"],
    "hatchback": ["red", "blue", "white", "silver", "black", "green"],
    "sedan": ["silver", "black", "blue", "red", "white", "green"],
    "van": ["white", "silver", "blue", "red"],
}

FIXED = {
    "trim": (42, 42, 45), "bumper": (58, 58, 62), "interior": (62, 56, 50), "carpet": (48, 44, 42),
    "seat": (52, 46, 44), "tire": (26, 26, 28), "rim": (150, 152, 158), "chrome": (178, 180, 186),
    "glass": (58, 78, 92), "headlight": (236, 232, 204), "taillight": (168, 26, 22), "plate": (232, 190, 40),
    "grille": (32, 32, 34), "rubber": (30, 30, 32), "saddle": (40, 36, 34), "metal": (112, 116, 122),
}


class Box:
    def __init__(self, origin, size, paint, inflate=0.0):
        self.origin = origin
        self.size = size
        # paint: material name for every face, or a dict {face or "all": material}.
        self.paint = paint if isinstance(paint, dict) else {"all": paint}
        self.inflate = inflate
        self.uv = None

    def face(self, name):
        return self.paint.get(name, self.paint.get("all"))


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rotation=(0, 0, 0), boxes=(), children=()):
        self.name = name
        self.pivot = pivot
        self.rotation = rotation
        self.boxes = list(boxes)
        self.children = list(children)

    def all_boxes(self):
        yield from self.boxes
        for child in self.children:
            yield from child.all_boxes()

    def to_json(self):
        return {
            "name": self.name,
            "pivot": list(self.pivot),
            "rotation": list(self.rotation),
            "boxes": [{"origin": list(b.origin), "size": list(b.size), "uv": list(b.uv), "inflate": b.inflate} for b in self.boxes],
            "children": [c.to_json() for c in self.children],
        }


def box(x0, y0, z0, x1, y1, z1, paint, inflate=0.0):
    """A box from its two corners."""
    return Box((x0, y0, z0), (x1 - x0, y1 - y0, z1 - z0), paint, inflate)


def wheel(name, x, y, z, radius, width, rim="rim"):
    """Two boxes, one turned 45 degrees: an octagon from the side."""
    side = {"left": ("wheel", rim), "right": ("wheel", rim), "all": "tread"}
    w = width
    return Part(name, (x, y, z), boxes=[box(-w / 2, -radius, -radius, w / 2, radius, radius, side)],
                children=[Part(name + "_b", rotation=(45, 0, 0),
                               boxes=[box(-w / 2, -radius, -radius, w / 2, radius, radius, side, inflate=-0.01)])])


# --- Cars ------------------------------------------------------------------------------------------

def car(length, front_axle, rear_axle, windshield_base, roof_front, roof_rear, rear_base, belt, roof,
        front_seat, rear_seat=None, floor=5, trunk_height=None, van=False):
    """A car body around a cabin: hood, trunk, doors, floor, seats, pillars, roof and glass."""
    half = length / 2
    hw = 13
    parts = []
    body_door = {"all": "body", "left": ("door", windshield_base, roof_rear), "right": ("door", windshield_base, roof_rear)}

    hood = Part("hood", boxes=[box(-hw, 4, windshield_base, hw, belt, half,
                                   {"all": "body", "front": "car_front", "top": "hood_top"})])
    parts.append(hood)
    if van:
        # The cargo box: from behind the front seats to the back doors, full height.
        parts.append(Part("cargo", boxes=[box(-hw, 4, -half, hw, roof, rear_base,
                                              {"all": ("panel", -half, rear_base), "back": "van_back", "top": "roof"})]))
    else:
        th = trunk_height or belt + 1
        parts.append(Part("trunk", boxes=[box(-hw, 4, -half, hw, th, rear_base, {"all": "body", "back": "car_back"})]))

    parts.append(Part("floor", boxes=[box(-hw + 2, 4, rear_base, hw - 2, floor, windshield_base, {"all": "trim", "top": "carpet"})]))
    parts.append(Part("door_left", boxes=[box(-hw, 4, rear_base, -hw + 2, belt, windshield_base,
                                               {"all": "interior", "left": body_door["left"], "top": "trim", "bottom": "trim"})]))
    parts.append(Part("door_right", boxes=[box(hw - 2, 4, rear_base, hw, belt, windshield_base,
                                                {"all": "interior", "right": body_door["right"], "top": "trim", "bottom": "trim"})]))
    parts.append(Part("dash", boxes=[box(-hw + 2, floor, windshield_base - 3, hw - 2, belt + 1, windshield_base, {"all": "interior", "top": "trim"}),
                                     # The steering wheel, in front of the driver (on the left).
                                     box(-10, belt, windshield_base - 5, -3, belt + 5, windshield_base - 4, {"all": "rubber", "front": "rubber"})]))
    seats = []
    for z in [front_seat] + ([rear_seat] if rear_seat is not None else []):
        for x0, x1 in ((-10, -2), (2, 10)):
            seats.append(box(x0, floor, z - 3, x1, floor + 3, z + 4, "seat"))
            seats.append(box(x0, floor + 3, z - 5, x1, belt + 5, z - 3, "seat"))
    parts.append(Part("seats", boxes=seats))

    # Roof, pillars and glass.
    parts.append(Part("roof", boxes=[box(-hw + 1, roof - 1, roof_rear, hw - 1, roof, roof_front, "roof")]))
    rise = roof - 1 - belt
    front_run = windshield_base - roof_front
    front_len = round(math.hypot(front_run, rise))
    front_angle = -math.degrees(math.atan2(front_run, rise))
    parts.append(Part("windshield", pivot=(0, belt, windshield_base), rotation=(front_angle, 0, 0), boxes=[
        box(-hw, 0, -1, -hw + 1, front_len, 0, "body"), box(hw - 1, 0, -1, hw, front_len, 0, "body")],
        children=[Part("glass_front", boxes=[box(-hw + 1, 0, -1, hw - 1, front_len, 0, "glass")])]))
    rear_run = roof_rear - rear_base
    rear_rise = roof - 1 - (trunk_height or belt + 1) if not van else rise
    rear_len = round(math.hypot(rear_run, rear_rise))
    rear_angle = math.degrees(math.atan2(rear_run, rear_rise))
    if not van:
        rear_y = trunk_height or belt + 1
        parts.append(Part("rear_pillars", pivot=(0, rear_y, rear_base), rotation=(rear_angle, 0, 0), boxes=[
            box(-hw, 0, 0, -hw + 1, rear_len, 1, "body"), box(hw - 1, 0, 0, hw, rear_len, 1, "body")],
            children=[Part("glass_rear", boxes=[box(-hw + 1, 0, 0, hw - 1, rear_len, 1, "glass")])]))
    # Side windows in two steps, to follow the slanted pillars.
    middle = (roof_front + roof_rear) / 2
    half_rise = belt + rise / 2
    for side, x0 in (("left", -hw), ("right", hw - 1)):
        front_low = windshield_base - front_run / 2 - 1
        rear_low = rear_base + rear_run / 2 + 1 if not van else rear_base
        parts.append(Part(f"glass_{side}", boxes=[
            box(x0, belt, rear_low, x0 + 1, half_rise, front_low, "glass"),
            box(x0, half_rise, roof_rear, x0 + 1, roof - 1, roof_front, "glass")]))
        parts.append(Part(f"pillar_{side}", boxes=[box(x0, belt, middle - 1, x0 + 1, roof - 1, middle + 1, "body", inflate=0.02)]))
        mirror_x = (-hw - 2, -hw) if side == "left" else (hw, hw + 2)
        parts.append(Part(f"mirror_{side}", boxes=[box(mirror_x[0], belt, windshield_base - 3, mirror_x[1], belt + 2, windshield_base - 1, "trim")]))

    parts.append(Part("bumpers", boxes=[
        box(-hw, 3, half, hw, 7, half + 1, {"all": "bumper", "front": "front_bumper"}),
        box(-hw, 3, -half - 1, hw, 7, -half, {"all": "bumper", "back": "rear_bumper"})]))
    for i, (x, z) in enumerate(((-14.5, front_axle), (14.5, front_axle), (-14.5, rear_axle), (14.5, rear_axle))):
        parts.append(wheel(f"wheel_{i}", x, 5, z, 5, 3))
    # Wheel arches: dark boxes inside the wheels.
    parts.append(Part("arches", boxes=[box(-hw, 4, z - 6, hw, 11, z + 6, {"all": "trim", "top": "body"}, inflate=0.05)
                                       for z in (front_axle, rear_axle)]))
    return parts


def hatchback():
    return car(56, 18, -18, windshield_base=12, roof_front=4, roof_rear=-20, rear_base=-24, belt=14, roof=27,
               front_seat=4, rear_seat=-10)


def sedan():
    return car(66, 21, -20, windshield_base=14, roof_front=5, roof_rear=-17, rear_base=-23, belt=14, roof=27,
               front_seat=6, rear_seat=-9, trunk_height=15)


def van():
    return car(72, 24, -22, windshield_base=22, roof_front=19, roof_rear=10, rear_base=10, belt=17, roof=35,
               front_seat=16, floor=8, van=True)


# --- Two wheelers ----------------------------------------------------------------------------------

def bicycle():
    r = 7
    frame = "frame"
    return [
        wheel("wheel_1", 0, r, -10, r, 1, rim="spokes"),
        Part("frame", boxes=[
            box(-0.5, r - 0.5, -10, 0.5, r + 0.5, -2, frame),                   # chain stay
            box(-0.5, r, -3, 0.5, 15, -2, frame),                               # seat tube
            box(-0.5, 13, -2, 0.5, 14, 8, frame),                               # top tube
            box(-1.5, 15, -5, 1.5, 16, -1, "saddle"),                            # saddle
            box(-1, r - 1, -4, 1, r + 1, -1, "metal"),                           # crank
            box(-3, r - 0.5, -3, 3, r + 0.5, -2, "metal"),                       # pedals
        ], children=[
            Part("down_tube", pivot=(0, r, -2), rotation=(-50, 0, 0), boxes=[box(-0.5, 0, -0.5, 0.5, 13, 0.5, frame)]),
            Part("seat_stay", pivot=(0, r, -10), rotation=(-48, 0, 0), boxes=[box(-0.5, 0, -0.5, 0.5, 11, 0.5, frame)]),
        ]),
        Part("steer", pivot=(0, 0, 9), children=[
            Part("fork", boxes=[box(-1, r, -0.5, 1, 17, 0.5, frame)]),
            Part("handlebar", boxes=[box(-5, 17, -0.5, 5, 18, 0.5, "rubber"), box(-1, 15, 0.5, 1, 17, 2, "chrome")]),
            wheel("wheel_0", 0, r, 1, r, 1, rim="spokes"),
        ]),
    ]


def scooter():
    r = 4
    return [
        wheel("wheel_1", 0, r, -11, r, 3),
        Part("body", boxes=[
            box(-3, 3, -6, 3, 5, 9, {"all": "trim", "top": "rubber"}),                     # floorboard
            box(-6, 5, -16, 6, 12, -4, {"all": "body", "back": "scooter_back"}),            # rear cowl
            box(-5, 12, -15, 5, 14, -3, "saddle"),                                          # seat
            box(-6, 4, 9, 6, 17, 11, {"all": "body", "front": "shield"}),                   # leg shield
            box(-1, 4, -10, 1, 6, -6, "metal"),                                             # swingarm
        ]),
        Part("steer", pivot=(0, 0, 11), children=[
            Part("column", boxes=[box(-1.5, r, -1, 1.5, 19, 2, "body"),
                                  box(-6, 18, -1, 6, 19.5, 1, "rubber"),
                                  box(-2.5, 17, 1, 2.5, 21, 3, {"all": "body", "front": "scooter_light"})]),
            Part("mudguard", boxes=[box(-2.5, 2 * r, -2, 2.5, 2 * r + 1, 5, "body")]),
            wheel("wheel_0", 0, r, 2, r, 3),
        ]),
    ]


MODELS = {"bicycle": (bicycle, 128), "scooter": (scooter, 128), "hatchback": (hatchback, 256), "sedan": (sedan, 256), "van": (van, 256)}


# --- UV packing --------------------------------------------------------------------------------------

def footprint(b):
    w, h, d = (math.ceil(v - 1e-6) for v in b.size)
    return max(1, 2 * (w + d)), max(1, d + h)


def pack(parts, width):
    """Shelf-packs every box's UV patch; returns the texture height."""
    boxes = sorted((b for p in parts for b in p.all_boxes()), key=lambda b: -footprint(b)[1])
    x = y = shelf = 0
    for b in boxes:
        fw, fh = footprint(b)
        if x + fw > width:
            x, y, shelf = 0, y + shelf, 0
        b.uv = (x, y)
        x += fw
        shelf = max(shelf, fh)
    height = y + shelf
    return 1 << max(5, math.ceil(math.log2(height)))


# --- Painting ----------------------------------------------------------------------------------------

def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c[:3]) + (255,)


class Painter:
    def __init__(self, img, color, wreck, rng):
        self.img = img
        self.color = color
        self.wreck = wreck
        self.rng = rng

    def put(self, x, y, c):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.img.putpixel((x, y), c)

    def fill(self, x0, y0, w, h, c, noise=4):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.put(x, y, shade(c, self.rng.randint(-noise, noise)))

    def base(self, material):
        if material in ("body", "roof", "hood_top", "frame", "panel", "door", "car_front", "car_back", "van_back",
                        "scooter_back", "shield", "scooter_light"):
            if self.wreck:
                return (66, 50, 40)
            return self.color
        if self.wreck and material in ("seat", "interior", "carpet", "saddle"):
            return (36, 32, 30)
        aliases = {"wheel": "tire", "tread": "tire", "front_bumper": "bumper", "rear_bumper": "bumper"}
        return FIXED[aliases.get(material, material)]

    def rust(self, x0, y0, w, h):
        """Burnt paint: soot and rust over the bare metal."""
        for _ in range(max(1, w * h // 10)):
            x, y = x0 + self.rng.randrange(w), y0 + self.rng.randrange(h)
            c = self.rng.choice([(140, 70, 30), (110, 56, 28), (24, 22, 20), (40, 34, 30)])
            self.put(x, y, c + (255,))

    def face(self, spec, face, x0, y0, w, h, box):
        if spec is None or w <= 0 or h <= 0:
            return
        name, *args = spec if isinstance(spec, tuple) else (spec,)
        top = face == "top"
        bottom = face == "bottom"
        c = self.base(name)
        light = 14 if top else -40 if bottom else 0
        self.fill(x0, y0, w, h, shade(c, light))
        painted = name in ("body", "roof", "hood_top", "panel", "door", "car_front", "car_back", "van_back", "frame",
                           "scooter_back", "shield", "scooter_light")
        if self.wreck and painted:
            self.rust(x0, y0, w, h)

        # Column -> z along the vehicle, for the side faces (the left face runs front to back).
        def z_of(col):
            z0, z1 = box.origin[2], box.origin[2] + box.size[2]
            return z1 - col - 0.5 if face == "left" else z0 + col + 0.5

        if name == "glass":
            # Mostly see-through (the models are cut out, not blended): a few reflections and a dark edge.
            for y in range(h):
                for x in range(w):
                    edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
                    streak = (x + (h - 1 - y)) % 9 in (0, 1) and (x + y) % 23 > 6
                    if edge:
                        self.put(x0 + x, y0 + y, shade(c, -20))
                    elif streak:
                        self.put(x0 + x, y0 + y, shade(c, 70))
                    else:
                        self.put(x0 + x, y0 + y, (0, 0, 0, 0))
        elif name == "wheel":
            rim = args[0]
            cx, cy = (w - 1) / 2, (h - 1) / 2
            r = min(w, h) / 2
            for y in range(h):
                for x in range(w):
                    d = math.hypot(x - cx, y - cy)
                    if rim == "spokes":
                        if d < r * 0.82 and (abs(x - cx) < 0.6 or abs(y - cy) < 0.6 or abs(abs(x - cx) - abs(y - cy)) < 0.8):
                            self.put(x0 + x, y0 + y, FIXED["chrome"] + (255,))
                        elif d < r * 0.82:
                            self.put(x0 + x, y0 + y, (0, 0, 0, 0))
                    elif d < r * 0.55:
                        self.put(x0 + x, y0 + y, shade(FIXED["rim"], -30 if d < r * 0.2 else 0))
        elif name == "tread":
            for y in range(0, h, 2):
                for x in range(w):
                    self.put(x0 + x, y0 + y, shade(FIXED["tire"], 10))
        elif name == "door" and face in ("left", "right"):
            front, rear = args
            for col in range(w):
                z = z_of(col)
                seam = abs(z - front + 1) < 0.6 or abs(z - rear - 1) < 0.6 or abs(z - (front + rear) / 2) < 0.6
                if seam:
                    for y in range(h):
                        self.put(x0 + col, y0 + y, shade(c, -45))
                handle = abs(z - (front - 4)) < 1.1 or abs(z - ((front + rear) / 2 - 4)) < 1.1
                if handle:
                    self.put(x0 + col, y0 + 2, shade(c, -60))
        elif name == "car_front" and face == "front":
            # Headlights at the corners, grille between, near the top of the hood's nose.
            for x in range(w):
                for y in range(2, min(h, 6)):
                    if x < 5 or x >= w - 5:
                        self.put(x0 + x, y0 + y, (60, 60, 60, 255) if self.wreck else shade(FIXED["headlight"], 0))
                    elif 7 <= x < w - 7 and y >= 3:
                        self.put(x0 + x, y0 + y, shade(FIXED["grille"], 8 if y % 2 else -4))
        elif name in ("car_back", "van_back", "scooter_back") and face == "back":
            for x in range(w):
                for y in range(2, min(h, 5)):
                    if x < 4 or x >= w - 4:
                        self.put(x0 + x, y0 + y, (50, 30, 30, 255) if self.wreck else shade(FIXED["taillight"], 10 if y == 2 else 0))
            if name == "van_back":
                mid = w // 2
                for y in range(h):
                    self.put(x0 + mid, y0 + y, shade(c, -45))
                for x in range(3, w - 3):
                    if x != mid:
                        for y in range(3, h // 2):
                            self.put(x0 + x, y0 + y - 1, shade(FIXED["glass"], 0) if not self.wreck else (20, 20, 20, 255))
        elif name in ("front_bumper", "rear_bumper") and face in ("front", "back"):
            for x in range(w // 2 - 4, w // 2 + 4):
                for y in range(1, 3):
                    self.put(x0 + x, y0 + y, shade(FIXED["plate"], 0) if (x + y) % 3 else (30, 30, 30, 255))
        elif name == "panel" and face in ("left", "right"):
            for col in range(0, w, 12):
                for y in range(h):
                    self.put(x0 + col, y0 + y, shade(c, -30))
        elif name == "shield" and face == "front":
            for x in range(w // 2 - 1, w // 2 + 1):
                for y in range(1, h - 1):
                    self.put(x0 + x, y0 + y, shade(FIXED["chrome"], 0))
        elif name == "scooter_light" and face == "front":
            self.fill(x0 + 1, y0 + 1, w - 2, h - 2, FIXED["headlight"] if not self.wreck else (60, 60, 60), 0)
        elif name == "seat" and not bottom:
            for x in range(0, w, 3):
                for y in range(h):
                    self.put(x0 + x, y0 + y, shade(c, -10))

    def paint_box(self, b):
        w, h, d = (math.ceil(v - 1e-6) for v in b.size)
        u, v = b.uv
        # Minecraft's box UV layout (see ModelPart.Cube).
        regions = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "left": (u, v + d, d, h), "back": (u + d, v + d, w, h),
            "right": (u + d + w, v + d, d, h), "front": (u + 2 * d + w, v + d, w, h),
        }
        for face, (x0, y0, fw, fh) in regions.items():
            self.face(b.face(face), face, x0, y0, fw, fh, b)


def main():
    for name, (build, width) in MODELS.items():
        parts = build()
        height = pack(parts, width)
        model = {"texture": [width, height], "parts": [p.to_json() for p in parts]}
        out = ROOT / "vehicles" / f"{name}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(model, indent="\t") + "\n")
        print("wrote", out.relative_to(ROOT.parents[3]))
        for color in VARIANTS[name] + ["wreck"]:
            img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
            painter = Painter(img, COLORS.get(color, (0, 0, 0)), color == "wreck", random.Random(f"{name}-{color}"))
            for part in parts:
                for b in part.all_boxes():
                    painter.paint_box(b)
            tex = ROOT / "textures/entity/vehicle" / name / f"{color}.png"
            tex.parent.mkdir(parents=True, exist_ok=True)
            img.save(tex)
        print("  textures:", ", ".join(VARIANTS[name] + ["wreck"]))


if __name__ == "__main__":
    main()
