"""Generates the vehicle models and their textures.

Run from the repo root:  python3 tools/vehicles/make_vehicles.py

A model is a tree of parts made of boxes, in pixels (16 = 1 block), with x to the vehicle's right,
y up and z forward. It's written as JSON (assets/outbreak/vehicles/<type>.json) that VehicleModel.java
turns into a Minecraft model. Every box gets its own patch of the texture (Minecraft's box UV
layout), painted per face at RES texels per model pixel (the model's UVs stay in pixels, so the
texture is just finer): panel seams, handles, lights, Dutch number plates, tire tread and alloy
rims, the dashboard dials, seat fabric, dirt.

Three textures per vehicle:
  base.png   everything that isn't paint (the paint is cut out of it); glass is half see-through
  paint.png  only the paint, in grey: drawn over the base tinted with the vehicle's colour
  wreck.png  burnt out: rust, soot, no glass
The colours are listed in the model JSON ("tints", in the order of VehicleType's colours).

Part names the game looks for:
  wheel_<n>        spins; hidden without a tire (0 front left, 1 front right, 2 rear left, 3 rear
                   right; bikes 0 front, 1 rear)
  steer            turns with the steering (bikes, scooters)
  steering_wheel   spins with the steering (cars)
  glass*           drawn see-through; hidden once the windows are broken
  grip_left/right  where the driver's hands go (first person arms, third person poses)
  ignition, key    the ignition you click to start the engine; the key shows when it's in it
  needle_speed, needle_fuel   dashboard needles
  crank            the bicycle's pedals, turning as it rolls
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/outbreak"
RES = 4

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
# The paint layer is grey (PAINT_GREY = the colour itself); the tint is a bit brighter than the
# colour so highlights can go above it.
PAINT_GREY = 204
TINT_BOOST = 255 / PAINT_GREY

FIXED = {
    "trim": (40, 40, 43), "bumper": (50, 50, 54), "plastic": (46, 45, 44), "carpet": (44, 42, 40),
    "seat": (54, 52, 56), "rubber": (28, 28, 30), "chrome": (186, 188, 194), "metal": (112, 116, 122),
    "glass": (38, 52, 62), "headliner": (124, 120, 112), "leather": (40, 34, 32), "saddle": (78, 52, 34),
    "dirt": (96, 86, 70), "well": (14, 14, 15), "needle": (236, 84, 30), "key": (168, 170, 174),
    "fob": (24, 24, 26), "mirror": (150, 168, 182), "exhaust": (70, 68, 66), "visor": (112, 108, 100),
}

# Dutch number plates: yellow, black letters, the blue EU band on the left.
PLATE_LETTERS = "BDFGHJKLNPRSTVXZ"
FONT = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "011", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "B": ["110", "101", "110", "101", "110"], "D": ["110", "101", "101", "101", "110"],
    "F": ["111", "100", "110", "100", "100"], "G": ["111", "100", "101", "101", "111"],
    "H": ["101", "101", "111", "101", "101"], "J": ["001", "001", "001", "101", "111"],
    "K": ["101", "101", "110", "101", "101"], "L": ["100", "100", "100", "100", "111"],
    "N": ["111", "101", "101", "101", "101"], "P": ["111", "101", "111", "100", "100"],
    "R": ["110", "101", "110", "101", "101"], "S": ["111", "100", "111", "001", "111"],
    "T": ["111", "010", "010", "010", "010"], "V": ["101", "101", "101", "101", "010"],
    "X": ["101", "101", "010", "101", "101"], "Z": ["111", "001", "010", "100", "111"],
    "-": ["000", "000", "111", "000", "000"],
}


class Box:
    def __init__(self, origin, size, paint, inflate=0.0):
        self.origin = origin
        self.size = size
        # paint: material for every face, or a dict {face or "all": material}; a material is a
        # name or a tuple (name, args...).
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
            "pivot": [round(v, 4) for v in self.pivot],
            "rotation": [round(v, 4) for v in self.rotation],
            "boxes": [{"origin": [round(v, 4) for v in b.origin], "size": [round(v, 4) for v in b.size], "uv": list(b.uv),
                       "inflate": b.inflate} for b in self.boxes],
            "children": [c.to_json() for c in self.children],
        }


def box(x0, y0, z0, x1, y1, z1, paint, inflate=0.0):
    """A box from its two corners."""
    return Box((x0, y0, z0), (x1 - x0, y1 - y0, z1 - z0), paint, inflate)


def wheel(name, x, y, z, radius, width, rim="alloy"):
    """A round wheel: the tire wall and the rim painted on the sides of one box (see-through outside
    the circle), and the tread made of six slabs turned 30 degrees apart, whose ends make a
    twelve-sided ring."""
    w = width
    r = radius
    d = 2 * r * math.tan(math.radians(15)) + 0.04
    side = {"left": ("wheel", rim, r), "right": ("wheel", rim, r), "all": "none"}
    slab = {"top": ("tread", rim), "bottom": ("tread", rim), "all": "none"}
    return Part(name, (x, y, z), boxes=[box(-w / 2, -r, -r, w / 2, r, r, side, inflate=0.01)],
                children=[Part(f"{name}_{i}", rotation=(30 * i, 0, 0), boxes=[box(-w / 2, -r, -d / 2, w / 2, r, d / 2, slab)])
                          for i in range(6)])


def ring(name, radius, thickness, segments, paint, pivot=(0, 0, 0)):
    """A ring in the xy plane (a steering wheel): short boxes all the way round."""
    length = 2 * radius * math.tan(math.pi / segments) + thickness * 0.4
    t = thickness / 2
    return Part(name, pivot, children=[
        Part(f"{name}_{i}", rotation=(0, 0, 360 * i / segments),
             boxes=[box(-length / 2, radius - t, -t, length / 2, radius + t, t, paint)]) for i in range(segments)])


# --- Cars ------------------------------------------------------------------------------------------

# Seat height (blocks, as in VehicleType) -> the driver's eye height in pixels: a sitting player's
# eye is 1.02 blocks above the seat point.
def eye_height(seat_y):
    return (seat_y + 1.02) * 16


class Car:
    """Shape numbers the painters need (where the wheels and doors are)."""
    def __init__(self, **kw):
        self.__dict__.update(kw)


CAR = Car(hw=13, axles=(), doors=(), belt=14, half=28)


def car(length, front_axle, rear_axle, windshield_base, roof_front, roof_rear, rear_base, belt, roof,
        front_seat, seat_y, rear_seat=None, floor=4.6, trunk_height=None, van=False):
    """A car body around a cabin: hood, trunk, doors, floor, dashboard, steering wheel, seats,
    pillars, roof and glass."""
    half = length / 2
    hw = 13
    eye = eye_height(seat_y)
    driver_x = -6.5
    CAR.__init__(hw=hw, axles=(front_axle, rear_axle), half=half, belt=belt, roof=roof, van=van,
                 doors=(windshield_base, rear_base), windshield_base=windshield_base)
    parts = []
    th = trunk_height or belt + 1
    rise = roof - 1 - belt
    front_run = windshield_base - roof_front
    slope = front_run / rise

    # --- Body
    parts.append(Part("hood", boxes=[box(-hw, 4, windshield_base, hw, belt, half,
                                         {"all": "body", "front": "car_front", "top": "hood_top", "bottom": "trim"})]))
    if van:
        parts.append(Part("cargo", boxes=[box(-hw, 4, -half, hw, roof, rear_base,
                                              {"all": "panel", "back": "van_back", "top": "roof", "bottom": "trim", "front": "plastic"})]))
    else:
        parts.append(Part("trunk", boxes=[box(-hw, 4, -half, hw, th, rear_base,
                                              {"all": "body", "back": "car_back", "top": "trunk_top", "bottom": "trim", "front": "plastic"})]))

    parts.append(Part("floor", boxes=[box(-hw + 2, 4, rear_base, hw - 2, floor, windshield_base, {"all": "trim", "top": "carpet"})]))
    for side, x0, x1, outer, inner in (("left", -hw, -hw + 2, "left", "right"), ("right", hw - 2, hw, "right", "left")):
        parts.append(Part(f"door_{side}", boxes=[box(x0, 4, rear_base, x1, belt, windshield_base,
                                                     {"all": "plastic", outer: "door", inner: "door_inner", "top": "rubber", "bottom": "trim"})]))

    # --- Dashboard: low in front of the passenger, the instrument cluster in front of the driver
    # where you see it through the steering wheel.
    dash_z = windshield_base - 4
    dash_top = eye - 3.2
    upper_front = windshield_base - (dash_top - belt) * slope - 0.2
    parts.append(Part("dash", boxes=[
        box(-hw + 2, floor, dash_z, hw - 2, belt, windshield_base, {"all": "plastic", "back": "dash_face", "top": "plastic"}),
        box(-hw + 2, belt, dash_z, hw - 2, dash_top, upper_front, {"all": "plastic", "back": "dash_face", "top": "dash_top"}),
        # The cluster, its hood over it, and the column shroud under the steering wheel.
        box(driver_x - 3.6, dash_top - 2.4, dash_z - 0.4, driver_x + 3.6, dash_top + 1.0, dash_z + 1.5,
            {"all": "plastic", "back": "cluster"}),
        box(driver_x - 3.9, dash_top + 1.0, dash_z - 1.1, driver_x + 3.9, dash_top + 1.6, dash_z + 1.5, {"all": "plastic", "top": "dash_top"}),
        box(driver_x - 1.3, dash_top - 5.6, dash_z - 2.2, driver_x + 1.3, dash_top - 3.6, dash_z, "plastic"),
        # Centre console with the gear stick and handbrake.
        box(-1.8, floor, front_seat - 2, 1.8, floor + 2.6, dash_z, {"all": "plastic", "top": "console"}),
    ], children=[
        Part("gear_stick", pivot=(0, floor + 2.6, dash_z - 3.5), rotation=(-12, 0, 0), boxes=[
            box(-0.25, 0, -0.25, 0.25, 2.4, 0.25, "chrome"), box(-0.6, 2.4, -0.6, 0.6, 3.4, 0.6, "leather")]),
        Part("handbrake", pivot=(0, floor + 2.6, front_seat), rotation=(14, 0, 0), boxes=[
            box(-0.4, 0, -3.2, 0.4, 0.7, 0, "plastic")]),
        Part("needle_speed", pivot=(driver_x - 1.85, dash_top - 0.75, dash_z - 0.45),
             boxes=[box(-0.12, -0.15, -0.05, 0.12, 1.15, 0.03, "needle")]),
        Part("needle_fuel", pivot=(driver_x + 1.85, dash_top - 0.75, dash_z - 0.45),
             boxes=[box(-0.12, -0.15, -0.05, 0.12, 1.0, 0.03, "needle")]),
        # The ignition, right of the column; the key with its fob when it's in.
        Part("ignition", pivot=(driver_x + 2.4, dash_top - 4.4, dash_z - 0.1), boxes=[
            box(-0.55, -0.55, -0.3, 0.55, 0.55, 0.1, {"all": "chrome", "back": "ignition"})], children=[
            Part("key", boxes=[box(-0.12, -0.32, -0.75, 0.12, 0.32, -0.3, "key"),
                               box(-0.5, -0.45, -1.35, 0.5, 0.75, -0.75, {"all": "fob", "back": "fob_face"}),
                               box(-0.08, -1.4, -1.1, 0.08, -0.45, -0.95, "key"),
                               box(-0.45, -2.3, -1.2, 0.45, -1.4, -0.85, {"all": "fob", "back": "fob_face"})])]),
    ]))

    # The steering wheel: tilted, top away from you, turning with the front wheels.
    radius = 3.3
    parts.append(Part("steering_column", pivot=(driver_x, eye - 3.6, dash_z - 1.8), rotation=(22, 0, 0), boxes=[
        box(-0.55, -0.55, 0, 0.55, 0.55, 2.4, "plastic")], children=[
        Part("steering_wheel", children=[
            ring("steering_ring", radius, 0.75, 12, "wheel_rim"),
            Part("steering_hub", boxes=[
                box(-1.25, -1.05, -0.55, 1.25, 1.05, 0.2, {"all": "rubber", "back": "airbag"}),
                box(-radius + 0.2, -0.35, -0.2, -1.2, 0.35, 0.15, "rubber"),
                box(1.2, -0.35, -0.2, radius - 0.2, 0.35, 0.15, "rubber"),
                box(-0.45, -radius + 0.2, -0.2, 0.45, -1.0, 0.15, "rubber")]),
            Part("grip_left", pivot=(-radius * 0.87, radius * 0.5, 0)),
            Part("grip_right", pivot=(radius * 0.87, radius * 0.5, 0)),
        ])]))

    # --- Seats with headrests.
    seats = []
    rows = [front_seat] + ([rear_seat] if rear_seat is not None else [])
    cushion = floor + 1.5
    for z in rows:
        for x0, x1 in ((-10.6, -2.2), (2.2, 10.6)):
            seats.append(box(x0, floor, z - 3, x1, cushion, z + 4.2, "seat"))
            seats.append(box(x0, cushion, z - 5, x1, belt + 4.6, z - 3, "seat"))
            seats.append(box(x0 + 1.6, belt + 4.9, z - 4.8, x1 - 1.6, belt + 7.3, z - 3.4, "headrest"))
    parts.append(Part("seats", boxes=seats))

    # --- Roof, pillars and glass.
    parts.append(Part("roof", boxes=[box(-hw + 1, roof - 1, roof_rear, hw - 1, roof, roof_front,
                                         {"all": "body", "top": "roof", "bottom": "headliner"})], children=[
        Part("visors", boxes=[box(-11, roof - 1.5, roof_front - 4.2, -2.2, roof - 1.05, roof_front - 0.3, "visor"),
                              box(2.2, roof - 1.5, roof_front - 4.2, 11, roof - 1.05, roof_front - 0.3, "visor")]),
        Part("rear_view_mirror", boxes=[box(-0.2, roof - 2.2, roof_front - 1.6, 0.2, roof - 1, roof_front - 1.2, "plastic"),
                                        box(-2.3, roof - 3.2, roof_front - 1.7, 2.3, roof - 2.0, roof_front - 1.05,
                                            {"all": "plastic", "back": "mirror_glass"})]),
        Part("antenna", pivot=(hw - 4, roof, roof_rear + 2), rotation=(-28, 0, 0), boxes=[box(-0.15, 0, -0.15, 0.15, 4.5, 0.15, "rubber")]),
    ]))
    front_len = math.hypot(front_run, rise)
    front_angle = -math.degrees(math.atan2(front_run, rise))
    parts.append(Part("windshield", pivot=(0, belt, windshield_base), rotation=(front_angle, 0, 0), boxes=[
        box(-hw, 0, -1, -hw + 1, front_len, 0, "body"), box(hw - 1, 0, -1, hw, front_len, 0, "body")],
        children=[Part("glass_front", boxes=[box(-hw + 1, 0, -0.8, hw - 1, front_len, -0.2, ("glass", "front"))])]))
    rear_run = roof_rear - rear_base
    rear_rise = roof - 1 - th if not van else rise
    rear_len = math.hypot(rear_run, rear_rise)
    rear_angle = math.degrees(math.atan2(rear_run, rear_rise))
    if not van:
        parts.append(Part("rear_pillars", pivot=(0, th, rear_base), rotation=(rear_angle, 0, 0), boxes=[
            box(-hw, 0, 0, -hw + 1, rear_len, 1, "body"), box(hw - 1, 0, 0, hw, rear_len, 1, "body")],
            children=[Part("glass_rear", boxes=[box(-hw + 1, 0, 0.2, hw - 1, rear_len, 0.8, ("glass", "rear"))])]))
    # Side windows in two steps, to follow the slanted pillars.
    middle = (roof_front + roof_rear) / 2
    half_rise = belt + rise / 2
    for side, x0 in (("left", -hw), ("right", hw - 1)):
        front_low = windshield_base - front_run / 2 - 0.6
        rear_low = rear_base + rear_run / 2 + 0.6 if not van else rear_base
        parts.append(Part(f"glass_{side}", boxes=[
            box(x0 + 0.2, belt, rear_low, x0 + 0.8, half_rise, front_low, ("glass", "side")),
            box(x0 + 0.2, half_rise, roof_rear, x0 + 0.8, roof - 1, roof_front, ("glass", "side"))]))
        parts.append(Part(f"pillar_{side}", boxes=[box(x0, belt, middle - 1, x0 + 1, roof - 1, middle + 1, "trim", inflate=0.02)]))
        mx = (-hw - 2.4, -hw) if side == "left" else (hw, hw + 2.4)
        parts.append(Part(f"mirror_{side}", boxes=[
            box(mx[0], belt + 0.2, windshield_base - 3.4, mx[1], belt + 2.2, windshield_base - 1.6, {"all": "body", "back": "mirror_glass"}),
            box(min(mx) + (1.4 if side == "left" else 0), belt, windshield_base - 2.8, max(mx) - (0 if side == "left" else 1.4), belt + 0.4,
                windshield_base - 2.0, "trim")]))

    # Wipers resting at the bottom of the windshield.
    parts.append(Part("wipers", boxes=[box(-11, belt, windshield_base + 0.5, -2, belt + 0.35, windshield_base + 0.9, "rubber"),
                                       box(1, belt, windshield_base + 0.5, 9.5, belt + 0.35, windshield_base + 0.9, "rubber")]))
    parts.append(Part("bumpers", boxes=[
        box(-hw - 0.3, 3, half - 0.4, hw + 0.3, 7, half + 1.2, {"all": "bumper", "front": "front_bumper"}),
        box(-hw - 0.3, 3, -half - 1.2, hw + 0.3, 7, -half + 0.4, {"all": "bumper", "back": "rear_bumper"}),
        box(hw - 6.4, 2.4, -half - 1.6, hw - 4.6, 3.6, -half + 1, {"all": "exhaust", "back": "exhaust_end"})]))
    # Wheels tucked half into the body, in dark wheel wells painted on its sides.
    for i, (x, z) in enumerate(((-hw + 0.5, front_axle), (hw - 0.5, front_axle), (-hw + 0.5, rear_axle), (hw - 0.5, rear_axle))):
        parts.append(wheel(f"wheel_{i}", x, 5, z, 5, 3.4))
    return parts


def hatchback():
    return car(56, 18, -18, windshield_base=12, roof_front=4, roof_rear=-20, rear_base=-24, belt=14, roof=27,
               front_seat=0, rear_seat=-12, seat_y=0.22)


def sedan():
    return car(66, 21, -20, windshield_base=14, roof_front=5, roof_rear=-17, rear_base=-23, belt=14, roof=27,
               front_seat=2, rear_seat=-11, seat_y=0.22, trunk_height=15)


def van():
    return car(72, 24, -22, windshield_base=22, roof_front=19, roof_rear=10, rear_base=10, belt=17, roof=35,
               front_seat=12, seat_y=0.5, floor=8, van=True)


# --- Two wheelers ----------------------------------------------------------------------------------

def fender(name, radius, width, start, end, step=22):
    """A mudguard over a wheel: short plates round the top, from start to end degrees (0 = top, +
    = forward)."""
    length = 2 * (radius + 1) * math.tan(math.radians(step / 2)) + 0.3
    return Part(name, children=[
        Part(f"{name}_{i}", rotation=(a, 0, 0), boxes=[box(-width / 2, radius + 0.6, -length / 2, width / 2, radius + 1.0, length / 2, "body")])
        for i, a in enumerate(range(start, end + 1, step))])


def bicycle():
    """A Dutch city bike: upright, swept-back handlebars, mudguards, chain guard, rack, dynamo light."""
    r = 7
    frame = "frame"
    return [
        wheel("wheel_1", 0, r, -10, r, 1.2, rim="spokes"),
        Part("rear_fender", pivot=(0, r, -10), children=[fender("rear_guard", r, 1.8, -110, 30)]),
        Part("frame", boxes=[
            box(-0.5, r - 0.5, -10, 0.5, r + 0.5, -2, frame),                   # chain stay
            box(-0.5, r, -3, 0.5, 15.4, -2, frame),                             # seat tube
            box(-0.25, 15.4, -2.75, 0.25, 16, -2.25, "chrome"),                 # seat post
            box(-1.6, 16, -5.4, 1.6, 16.9, -1.2, "saddle"),                     # saddle
            box(-1.2, 15.6, -5, -0.8, 16, -4.4, "chrome"), box(0.8, 15.6, -5, 1.2, 16, -4.4, "chrome"),  # springs
            box(0.6, r - 1.6, -10.4, 1.2, r + 1.4, -1.6, {"all": "body", "right": "chain_guard"}),  # chain guard
            box(-1.6, 14.6, -16, 1.6, 15, -5.4, "metal"),                       # rack
            box(-1.7, 14.2, -16.2, 1.7, 14.6, -15.6, "metal"),
            box(-0.6, 12.6, -16.3, 0.6, 13.6, -15.9, {"all": "trim", "back": "reflector"}),  # rear light
            box(-0.2, 3.5, -4, 0.2, r - 0.5, -3.6, "metal"),                    # kickstand
        ], children=[
            Part("down_tube", pivot=(0, r, -2), rotation=(-50, 0, 0), boxes=[box(-0.5, 0, -0.5, 0.5, 13, 0.5, frame)]),
            Part("low_tube", pivot=(0, r + 1, -2), rotation=(-66, 0, 0), boxes=[box(-0.45, 0, -0.45, 0.45, 11.5, 0.45, frame)]),
            Part("seat_stay", pivot=(0, r, -10), rotation=(-48, 0, 0), boxes=[box(-0.5, 0, -0.5, 0.5, 11, 0.5, frame)]),
            Part("rack_stay", pivot=(0, r, -10), rotation=(-20, 0, 0), boxes=[box(-1.4, 0, -0.2, -1.1, 7.8, 0.2, "metal"),
                                                                              box(1.1, 0, -0.2, 1.4, 7.8, 0.2, "metal")]),
        ]),
        Part("crank", pivot=(0, r, -2.5), boxes=[box(-1.5, -1.5, -1.5, -0.6, 1.5, 1.5, {"all": "metal", "left": "chainring"}),
                                                 box(0.6, -0.4, -0.4, 1.0, 0.4, 0.4, "metal")], children=[
            Part("crank_arm_r", boxes=[box(1.0, -0.3, -0.3, 1.4, 0.3, 2.5, "metal"), box(1.4, -0.35, 1.9, 3.0, 0.35, 2.9, "rubber")]),
            Part("crank_arm_l", boxes=[box(-1.4, -0.3, -2.5, -1.0, 0.3, 0.3, "metal"), box(-3.0, -0.35, -2.9, -1.4, 0.35, -1.9, "rubber")]),
        ]),
        Part("steer", pivot=(0, 0, 9), children=[
            Part("fork", boxes=[box(-1, r, -0.5, 1, 17, 0.5, frame), box(-0.4, 15.2, 0.5, 0.4, 16.6, 1.6, {"all": "chrome", "front": "lamp"})]),
            Part("handlebar", boxes=[
                box(-0.4, 17, -0.4, 0.4, 18.4, 0.4, "chrome"),
                box(-5.5, 17.8, -0.3, 5.5, 18.4, 0.3, "chrome"),
                box(-5.5, 17.8, -3.2, -4.9, 18.4, -0.3, "chrome"), box(4.9, 17.8, -3.2, 5.5, 18.4, -0.3, "chrome"),
                box(-5.65, 17.65, -4.6, -4.75, 18.55, -2.4, "rubber"), box(4.75, 17.65, -4.6, 5.65, 18.55, -2.4, "rubber"),
                box(-0.7, 18.4, -0.2, 0.7, 18.9, 0.6, "chrome")],            # bell
                children=[Part("grip_left", pivot=(-5.2, 18.1, -3.6)), Part("grip_right", pivot=(5.2, 18.1, -3.6))]),
            Part("front_fender", pivot=(0, r, 1), children=[fender("front_guard", r, 1.8, -40, 100)]),
            wheel("wheel_0", 0, r, 1, r, 1.2, rim="spokes"),
        ]),
    ]


def scooter():
    """A Dutch moped: leg shield, round headlight, speedometer and ignition on the headset."""
    r = 4
    return [
        wheel("wheel_1", 0, r, -11, r, 3, rim="steel"),
        Part("body", boxes=[
            box(-3, 3, -6, 3, 5, 9, {"all": "trim", "top": "floorboard"}),                    # floorboard
            box(-6, 5, -16, 6, 12, -4, {"all": "body", "back": "scooter_back", "bottom": "trim"}),  # rear cowl
            box(-5, 12, -15, 5, 14, -3, {"all": "saddle", "top": "scooter_seat"}),             # seat
            box(-6, 4, 9, 6, 17, 11, {"all": "body", "front": "shield", "back": "plastic"}),   # leg shield
            box(-1, 4, -10, 1, 6, -6, "metal"),                                               # swingarm
            box(-5.4, 9, -16.4, 5.4, 10.6, -15.8, {"all": "trim", "back": "moped_plate"}),     # plate
            box(2.5, 3.2, -15, 3.7, 4.4, -8, {"all": "exhaust", "back": "exhaust_end"}),       # exhaust
        ]),
        Part("steer", pivot=(0, 0, 11), children=[
            Part("column", boxes=[box(-1.5, r, -1, 1.5, 19, 2, "body"),
                                  box(-6, 18, -1, 6, 19.5, 1, "chrome"),
                                  box(-6.8, 17.8, -1.2, -4.6, 19.7, 1.2, "rubber"), box(4.6, 17.8, -1.2, 6.8, 19.7, 1.2, "rubber"),
                                  box(-2.6, 19, -1.8, 2.6, 21.6, 3, {"all": "body", "front": "scooter_light", "back": "scooter_dash"}),
                                  box(-5.4, 19.5, -0.2, -5.0, 22.4, 0.2, "chrome"), box(5.0, 19.5, -0.2, 5.4, 22.4, 0.2, "chrome"),
                                  box(-6.4, 22.4, -0.4, -4.4, 23.6, 0.2, {"all": "chrome", "back": "mirror_glass"}),
                                  box(4.4, 22.4, -0.4, 6.4, 23.6, 0.2, {"all": "chrome", "back": "mirror_glass"})],
                 children=[Part("grip_left", pivot=(-5.7, 18.75, 0)), Part("grip_right", pivot=(5.7, 18.75, 0)),
                           Part("needle_speed", pivot=(-0.9, 20.3, -1.85), boxes=[box(-0.1, -0.1, -0.04, 0.1, 0.9, 0.02, "needle")]),
                           Part("ignition", pivot=(1.3, 19.6, -1.85), boxes=[
                               box(-0.4, -0.4, -0.2, 0.4, 0.4, 0.05, {"all": "chrome", "back": "ignition"})], children=[
                               Part("key", boxes=[box(-0.1, -0.25, -0.6, 0.1, 0.25, -0.2, "key"),
                                                  box(-0.4, -0.35, -1.1, 0.4, 0.6, -0.6, {"all": "fob", "back": "fob_face"})])])]),
            Part("mudguard", boxes=[box(-2.5, 2 * r, -2, 2.5, 2 * r + 1, 5, "body")]),
            wheel("wheel_0", 0, r, 2, r, 3, rim="steel"),
        ]),
    ]


MODELS = {"bicycle": (bicycle, 128), "scooter": (scooter, 128), "hatchback": (hatchback, 256), "sedan": (sedan, 256), "van": (van, 256)}


# --- UV packing --------------------------------------------------------------------------------------

def footprint(b):
    w, h, d = (math.ceil(v - 1e-6) for v in b.size)
    return max(1, 2 * (w + d)), max(1, d + h)


def pack(parts, width):
    """Shelf-packs every box's UV patch; returns the texture height."""
    boxes = sorted((b for p in parts for b in p.all_boxes()), key=lambda b: (-footprint(b)[1], -footprint(b)[0]))
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

def clamp(v, lo=0, hi=255):
    return lo if v < lo else hi if v > hi else v


def tone(c, f, d=0):
    """A colour scaled by f, plus d."""
    return (clamp(int(c[0] * f + d)), clamp(int(c[1] * f + d)), clamp(int(c[2] * f + d)), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Px:
    """One texel being painted: which face, where on it (u, v in pixels from the face's top left as
    you look at it), where in the part (x, y, z), and two noises in [-1, 1]: fine and smooth."""
    __slots__ = ("face", "u", "v", "w", "h", "x", "y", "z", "n", "s", "box")


def plate_text(rng):
    letters = lambda k: "".join(rng.choice(PLATE_LETTERS) for _ in range(k))
    digits = lambda k: "".join(rng.choice("0123456789") for _ in range(k))
    return rng.choice([f"{letters(2)}-{digits(3)}-{letters(1)}", f"{digits(2)}-{letters(3)}-{digits(1)}",
                       f"{letters(1)}-{digits(3)}-{letters(2)}", f"{digits(1)}-{letters(3)}-{digits(2)}"])


def plate(p, cx, cy, width, height, text, colour=(232, 186, 30)):
    """A number plate centred at (cx, cy) on the face (in u, v pixels), or None off it."""
    du, dv = p.u - (cx - width / 2), p.v - (cy - height / 2)
    if not (0 <= du < width and 0 <= dv < height):
        return None
    tu, tv = int(du * RES), int(dv * RES)
    tw, th = int(width * RES), int(height * RES)
    if tu == 0 or tv == 0 or tu == tw - 1 or tv == th - 1:
        return (20, 20, 20)
    band = max(3, tw // 10)
    if tu <= band:
        # The EU band: blue, a ring of yellow stars, NL.
        if tv == th // 2 - 1 and tu == band // 2:
            return (230, 200, 40)
        return (24, 52, 150)
    gx = tu - band - 1
    gy = tv - (th - 5) // 2
    char = gx // 4
    if 0 <= gy < 5 and 0 <= char < len(text) and gx % 4 < 3:
        if FONT.get(text[char], ["000"] * 5)[gy][gx % 4] == "1":
            return (16, 16, 16)
    return colour


def disc(p, cx, cy):
    """Distance from (cx, cy) on the face, and the angle (0 up, clockwise)."""
    dx, dy = p.u - cx, p.v - cy
    return math.hypot(dx, dy), math.atan2(dx, -dy)


def rounded(p, u0, v0, u1, v1, r):
    """Whether the texel is inside a rounded rectangle."""
    if not (u0 <= p.u < u1 and v0 <= p.v < v1):
        return False
    cu = min(max(p.u, u0 + r), u1 - r)
    cv = min(max(p.v, v0 + r), v1 - r)
    return math.hypot(p.u - cu, p.v - cv) <= r


def fine_line(a, b, width=1):
    """Whether a and b are within width texels."""
    return abs(a - b) * RES < width * 0.5 + 0.01


class Painter:
    def __init__(self, name, model_rng):
        self.name = name
        self.rng = model_rng
        self.plate = plate_text(model_rng)
        self.moped_plate = "".join(model_rng.choice(PLATE_LETTERS) for _ in range(3)) + "-" + str(model_rng.randint(10, 99))

    # Each returns ("P", lum) for paint, an RGB(A) tuple, or None (see-through).

    def shade(self, p, spec):
        name, *args = spec if isinstance(spec, tuple) else (spec,)
        fn = getattr(self, "m_" + name, None)
        if fn is not None:
            return fn(p, *args)
        return self.flat(p, FIXED.get(name, (255, 0, 255)))

    def flat(self, p, c, grain=0.04):
        light = 1.1 if p.face == "top" else 0.62 if p.face == "bottom" else 1.0
        return tone(c, light * (1 + p.n * grain + p.s * 0.03))

    # --- Paint

    def paint(self, p, dirt=True):
        if p.face == "top":
            lum = 1.08
        elif p.face == "bottom":
            return tone(FIXED["trim"], 0.7)
        else:
            # Lighter towards the top, with a bright reflection along the shoulder line.
            lum = 0.9 + 0.14 * clamp((p.y - 4) / 12, 0, 1)
            if p.face in ("left", "right") and fine_line(p.y, CAR.belt - 1.4, 2):
                lum += 0.12
        lum += p.s * 0.025 + p.n * 0.012
        if dirt and p.face != "top" and p.y < 8:
            grime = clamp((7.5 - p.y) / 4, 0, 1) * (0.25 + 0.3 * p.s)
            if (p.n + 1) / 2 < grime:
                return tone(FIXED["dirt"], 1 + p.n * 0.15)
        return ("P", lum)

    def well(self, p):
        """The wheel wells and their black arches, on the sides of the car."""
        if CAR.axles and p.face in ("left", "right"):
            for axle in CAR.axles:
                d = math.hypot(p.z - axle, p.y - 5)
                if d < 6.0:
                    return tone(FIXED["well"], 1 + p.n * 0.2)
                if d < 6.7:
                    return tone(FIXED["trim"], 1 + p.n * 0.1)
        return None

    def m_body(self, p):
        return self.well(p) or self.paint(p)

    def m_frame(self, p):
        return self.paint(p, dirt=False)

    def m_hood_top(self, p):
        if fine_line(abs(p.x), CAR.hw * 0.42) and p.z > CAR.windshield_base + 1.5:
            return ("P", 0.92)
        if fine_line(abs(p.x), CAR.hw * 0.42 - 0.25) and p.z > CAR.windshield_base + 1.5:
            return ("P", 1.2)
        if fine_line(p.z, CAR.windshield_base + 1.4):
            return (24, 24, 26)
        if fine_line(p.z, CAR.windshield_base + 1.8) and fine_line(abs(p.x), 4, 2):
            return (20, 20, 20)
        return self.paint(p)

    def m_trunk_top(self, p):
        if fine_line(p.z, CAR.doors[1] - 0.6) or fine_line(abs(p.x), CAR.hw - 1.2) and p.z < CAR.doors[1] - 0.6:
            return (28, 28, 30)
        return self.paint(p)

    def m_roof(self, p):
        if fine_line(abs(p.x), CAR.hw - 1.6):
            return (30, 30, 32)
        return self.paint(p)

    def m_door(self, p):
        well = self.well(p)
        if well:
            return well
        front, rear = CAR.doors
        if p.y > CAR.belt - 0.35:
            return tone(FIXED["rubber"], 1 + p.n * 0.1)
        if fine_line(p.y, 4.5) or fine_line(p.z, front - 0.4):
            return ("P", 0.5)
        if CAR.van:
            joints = (rear + 0.4,)
            handles = (front - 3.4,)
        else:
            middle = (front + rear) / 2
            joints = (middle, rear + 0.4)
            handles = (front - 3.4, middle - 3.4)
        if any(fine_line(p.z, j) for j in joints):
            return ("P", 0.5)
        hy = CAR.belt - 1.9
        for hz in handles:
            if abs(p.z - hz) < 1.3 and abs(p.y - hy) < 0.45:
                if abs(p.z - hz) < 1.05 and abs(p.y - hy) < 0.25:
                    return tone(FIXED["chrome"], 1.0 + (0.15 if p.v % 1 < 0.5 else -0.05))
                return ("P", 0.55)
        if abs(p.y - 7.9) < 0.4:
            return tone(FIXED["trim"], 1 + p.n * 0.08)
        return self.paint(p)

    def m_panel(self, p):
        """The van's cargo sides: ribs, the sliding door on the right."""
        well = self.well(p)
        if well:
            return well
        if p.face in ("left", "right"):
            if fine_line(p.y, 4.5):
                return ("P", 0.55)
            if p.face == "right":
                d0, d1 = CAR.doors[1] - 16, CAR.doors[1] - 0.5
                if (fine_line(p.z, d0) or fine_line(p.z, d1)) and p.y < CAR.roof - 2:
                    return ("P", 0.5)
                if fine_line(p.y, CAR.roof - 2) and d0 - 4 < p.z < d1:
                    return (30, 30, 32)
                if abs(p.z - (d1 - 2)) < 0.3 and abs(p.y - (CAR.belt - 1)) < 1.4:
                    return tone(FIXED["chrome"], 0.9)
            if fine_line(p.y, CAR.belt + 2) or fine_line(p.y, CAR.belt + 2.4):
                return ("P", 0.86)
        return self.paint(p)

    def m_car_front(self, p):
        if p.face != "front":
            return self.paint(p)
        hw, top = CAR.hw, CAR.belt
        ax = abs(p.x)
        # Headlights: a clear lens over a chrome reflector, the projector, a LED strip, the indicator.
        if rounded(p, *self.light_box(p), 0.5):
            u0, v0, u1, v1 = self.light_box(p)
            if p.u - u0 < 0.26 or u1 - p.u < 0.26 or p.v - v0 < 0.26 or v1 - p.v < 0.26:
                return (34, 34, 36)
            if ax > hw - 2.2:
                return (226, 140, 30) if p.n > -0.6 else (250, 180, 60)
            proj = math.hypot(ax - (hw - 4.9), p.y - (top - 2.0))
            if proj < 0.55:
                return (250, 250, 244)
            if proj < 0.85:
                return (60, 62, 66)
            if p.y < top - 3.0:
                return (240, 246, 255)
            return tone(FIXED["chrome"], 0.9 + 0.25 * clamp((top - p.y - 1) / 2, 0, 1) + p.n * 0.05)
        # Grille: chrome frame, dark slats, the badge.
        if ax < hw - 7.6 and top - 4.3 < p.y < top - 0.8:
            if math.hypot(p.x, p.y - (top - 2.5)) < 0.75:
                return (40, 44, 60) if math.hypot(p.x, p.y - (top - 2.5)) < 0.45 else FIXED["chrome"]
            if ax > hw - 7.85 or p.y > top - 1.05 or p.y < top - 4.05:
                return tone(FIXED["chrome"], 0.9)
            return (22, 22, 24) if int(p.v * RES) % 3 else (64, 64, 68)
        return self.paint(p)

    def light_box(self, p):
        """The headlight on this texel's side of the front face (u runs from +x to -x)."""
        hw = CAR.hw
        if p.x > 0:
            return (0.6, 0.8, 6.6, 3.6)
        return (2 * hw - 6.6, 0.8, 2 * hw - 0.6, 3.6)

    def m_car_back(self, p):
        if p.face != "back":
            return self.paint(p)
        hw = CAR.hw
        ax = abs(p.x)
        top = p.box.origin[1] + p.box.size[1]
        if ax > hw - 5.6 and top - 3.8 < p.y < top - 0.9:
            if ax > hw - 5.35 and top - 3.55 < p.y < top - 1.15:
                if ax < hw - 4.4:
                    return (236, 236, 230)
                if p.y > top - 1.75:
                    return (226, 128, 28)
                inner = top - 3.25 < p.y < top - 2.0 and ax < hw - 0.8
                return (200, 30, 26) if inner else (140, 18, 16)
            return (34, 30, 30)
        if fine_line(ax, hw - 6) and 7.5 < p.y < top - 0.4 or fine_line(p.y, 7.5) and ax < hw - 6:
            return ("P", 0.5)
        if abs(p.x) < 1.1 and abs(p.y - 8.6) < 0.3:
            return tone(FIXED["chrome"], 0.95)
        if abs(p.y - (top - 2.4)) < 0.2 and 2.2 < -p.x < 5.8 and int(p.u * RES) % 3:
            return tone(FIXED["chrome"], 1.0)
        return self.paint(p)

    def m_van_back(self, p):
        if p.face != "back":
            return self.paint(p)
        hw = CAR.hw
        top = CAR.roof
        if abs(p.x) > hw - 1.9 and 6 < p.y < 13:
            if p.y > 12:
                return (226, 128, 28)
            if p.y < 7.2:
                return (236, 236, 230)
            return (170, 24, 20)
        if fine_line(p.x, 0) or fine_line(p.y, 4.6):
            return ("P", 0.5)
        if 2.0 < abs(p.x) < hw - 2.5 and top - 9 < p.y < top - 2.5:
            if abs(p.x) < 2.3 or abs(p.x) > hw - 2.8 or p.y < top - 8.7 or p.y > top - 2.8:
                return tone(FIXED["rubber"], 1)
            return tone(FIXED["glass"], 0.8 + 0.4 * ((p.u + p.v) % 4 < 0.7))
        if 0.4 < abs(p.x) < 1.4 and abs(p.y - (top - 13)) < 0.35:
            return tone(FIXED["chrome"], 0.95)
        return self.paint(p)

    def m_chain_guard(self, p):
        if fine_line(p.y, p.box.origin[1] + 0.4) or fine_line(p.y, p.box.origin[1] + p.box.size[1] - 0.4):
            return ("P", 0.6)
        return self.paint(p, dirt=False)

    def m_shield(self, p):
        if abs(p.x) < 0.3:
            return tone(FIXED["chrome"], 1)
        return self.paint(p, dirt=False)

    def m_scooter_back(self, p):
        if abs(p.x) < 2.2 and 9.6 < p.y < 11.4:
            return (200, 30, 26) if abs(p.x) < 1.9 and 9.9 < p.y < 11.1 else (40, 34, 34)
        return self.paint(p, dirt=False)

    def m_scooter_light(self, p):
        d, _ = disc(p, p.w / 2, p.h / 2)
        if d < 1.05:
            return (250, 248, 232) if d < 0.6 else tone(FIXED["chrome"], 1.1 - d * 0.2)
        if d < 1.25:
            return tone(FIXED["chrome"], 0.8)
        return self.paint(p, dirt=False)

    def m_scooter_dash(self, p):
        d, a = disc(p, 1.7, 1.3)
        if d < 0.95:
            return self.dial(d, a, 0.95)
        return self.flat(p, FIXED["plastic"])

    def m_scooter_seat(self, p):
        if fine_line(abs(p.x), 3.6) and int(p.v * RES) % 2:
            return (110, 100, 90)
        return self.flat(p, FIXED["saddle"], 0.07)

    def m_saddle(self, p):
        if p.face != "bottom" and fine_line(p.y, p.box.origin[1] + p.box.size[1] - 0.3) and int((p.u + p.v) * RES) % 2:
            return (150, 120, 90)
        return self.flat(p, FIXED["saddle"], 0.07)

    def m_floorboard(self, p):
        return (34, 34, 36) if int(p.u * RES) % 4 == 0 else self.flat(p, FIXED["rubber"])

    def m_moped_plate(self, p):
        return plate(p, p.w / 2, p.h / 2, p.w - 0.2, p.h - 0.15, self.moped_plate, colour=(60, 120, 200)) or (20, 20, 20)

    # --- Glass, lights

    def m_glass(self, p, kind="side"):
        edge = min(p.u, p.v, p.w - p.u, p.h - p.v)
        if p.face in ("top", "bottom") or (p.face in ("front", "back") and kind == "side") or \
                (p.face in ("left", "right") and kind != "side"):
            return tone(FIXED["rubber"], 1)
        if edge < 0.35:
            return tone(FIXED["rubber"], 1)
        if kind == "front" and edge < 1.1 and p.v < 1.1:
            # The black frit band along the top.
            return (16, 16, 18, 230) if int(p.u * RES) % 2 or p.v < 0.8 else (16, 16, 18, 120)
        if kind == "front" and p.face == "front" and 1.5 < p.u < 2.6 and 1.4 < p.v < 2.4:
            return (40, 120, 180, 220)                  # parking permit
        if kind == "rear" and int(p.v * RES) % 5 == 0 and 2.5 < p.u < p.w - 2.5 and p.v > 1.5:
            return (70, 40, 30, 150)                    # demister lines
        # A soft sheen, lighter towards the top.
        sheen = clamp(1 - p.v / max(p.h, 0.1), 0, 1)
        if sheen > 0.7:
            return mix(FIXED["glass"], (150, 170, 182), (sheen - 0.7) / 0.3)[:3] + (int(96 + 40 * (sheen - 0.7) / 0.3),)
        return FIXED["glass"] + (96,)

    def m_mirror_glass(self, p):
        t = clamp(p.v / max(p.h, 0.1), 0, 1)
        c = mix((196, 214, 226), (96, 112, 124), t)
        if 0.4 < (p.u - p.v) % 3 < 0.8:
            c = tone(c, 1.2)
        return c

    def m_reflector(self, p):
        return (190, 26, 22) if int((p.u + p.v) * RES) % 2 else (230, 60, 50)

    def m_lamp(self, p):
        d, _ = disc(p, p.w / 2, p.h / 2)
        return (250, 248, 230) if d < 0.45 else FIXED["chrome"]

    # --- Bumpers and plates

    def m_bumper(self, p):
        if p.face in ("left", "right", "top"):
            return self.flat(p, FIXED["bumper"])
        return self.flat(p, FIXED["bumper"], 0.06)

    def m_front_bumper(self, p):
        if p.face != "front":
            return self.m_bumper(p)
        w = p.w
        got = plate(p, w / 2, 1.9, 10.4, 2.2, self.plate)
        if got:
            return got
        for cx in (2.6, w - 2.6):
            d, _ = disc(p, cx, 2.2)
            if d < 0.9:
                return (236, 236, 226) if d < 0.6 else tone(FIXED["chrome"], 0.85)
        if abs(p.u - w / 2) < 7 and p.v > 3.2:
            return (18, 18, 20) if (int(p.u * RES) + int(p.v * RES)) % 3 else (40, 40, 44)
        return self.m_bumper(p)

    def m_rear_bumper(self, p):
        if p.face != "back":
            return self.m_bumper(p)
        w = p.w
        got = plate(p, w / 2, 1.9, 10.4, 2.2, self.plate)
        if got:
            return got
        if (1.2 < p.u < 4.0 or w - 4.0 < p.u < w - 1.2) and 1.6 < p.v < 2.3:
            return (180, 24, 20) if int(p.u * RES) % 2 else (220, 50, 40)
        return self.m_bumper(p)

    def m_exhaust_end(self, p):
        d, _ = disc(p, p.w / 2, p.h / 2)
        return (14, 12, 12) if d < 0.35 else tone(FIXED["exhaust"], 0.9 + p.n * 0.1)

    # --- Wheels

    def m_none(self, p):
        return None

    def m_tread(self, p, rim="alloy"):
        # The tread runs round the wheel along v; grooves along it, sipes across.
        ax = (p.u / max(p.w, 0.1)) - 0.5
        rubber = FIXED["rubber"]
        if rim == "spokes":
            return tone(rubber, 1.2 if int(p.v * RES) % 3 == 0 else 1.0)
        if abs(abs(ax) - 0.2) < 0.05:
            return tone(rubber, 0.6)
        if abs(ax) > 0.38:
            return tone(rubber, 0.85 if int(p.v * RES) % 3 else 0.6)
        if int(p.v * RES + (2 if ax > 0 else 0)) % 4 == 0:
            return tone(rubber, 0.65)
        return tone(rubber, 1.05 + p.n * 0.08)

    def m_wheel(self, p, rim, radius):
        d, a = disc(p, radius, radius)
        r = radius
        if d > r:
            return None
        rubber = FIXED["rubber"]
        if rim == "spokes":
            if d > r - 0.7:
                return tone(rubber, 1.0 + p.n * 0.1)
            if d > r - 1.15:
                return tone(FIXED["chrome"], 0.85 + 0.15 * math.cos(a * 2))
            if d < 0.55:
                return tone(FIXED["chrome"], 1.0 if d < 0.35 else 0.8)
            # Sixteen spokes, crossing.
            spoke = (a / (2 * math.pi) * 16 + d * 0.35) % 1
            if spoke < 0.09 * (2.5 / max(d, 0.5)) * 0.5 + 0.035:
                return tone(FIXED["chrome"], 0.95)
            return None
        if d > r - 0.12:
            return tone(rubber, 0.7)
        rim_r = r * (0.62 if rim == "alloy" else 0.58)
        if d > rim_r:
            # The tire wall, with a ring of raised lettering.
            if abs(d - r * 0.82) < 0.12 and (a % 0.9) < 0.55 and math.sin(a * 3) > -0.2:
                return tone(rubber, 1.9 if int((a * 40) % 2) else 1.5)
            return tone(rubber, 1.0 + 0.08 * math.cos(a - 0.7) + p.n * 0.06)
        if d > rim_r - 0.25:
            return tone(FIXED["chrome"], 1.15)
        if rim == "steel":
            if d < 0.5:
                return tone(FIXED["chrome"], 0.9)
            for k in range(4):
                if math.hypot(p.u - r - math.sin(k * math.pi / 2) * rim_r * 0.55, p.v - r + math.cos(k * math.pi / 2) * rim_r * 0.55) < 0.3:
                    return (20, 20, 20)
            return tone((54, 54, 58), 1 + p.n * 0.05)
        # A five-spoke alloy: the brake disc and caliper show between the spokes.
        if d < r * 0.2:
            for k in range(5):
                ka = k * 2 * math.pi / 5
                if math.hypot(p.u - r - math.sin(ka) * r * 0.13, p.v - r + math.cos(ka) * r * 0.13) < 0.16:
                    return (60, 60, 64)
            return tone(FIXED["chrome"], 1.05 if d < r * 0.08 else 0.92)
        spoke = abs(((a / (2 * math.pi) * 5) % 1) - 0.5)
        width = 0.36 - 0.12 * (d / rim_r)
        if spoke > 0.5 - width / 2 + 0.0:
            return tone((168, 172, 178), 1.0 + 0.12 * math.cos(a - 0.6) - 0.15 * (spoke - 0.4))
        if 0.3 * r < d < 0.55 * r:
            if 0.55 < (a % (2 * math.pi)) < 1.25:
                return (70, 70, 74)                     # caliper
            return tone((96, 96, 98), 1 + p.n * 0.1)    # disc
        return (24, 24, 26)

    # --- Inside

    def m_plastic(self, p):
        return self.flat(p, FIXED["plastic"], 0.05)

    def m_carpet(self, p):
        front, rear = CAR.doors
        # Rubber mats in the footwells.
        if 3 < abs(p.x) < 10 and front - 9 < p.z < front - 1 and not (fine_line(abs(p.x), 3.2) or fine_line(abs(p.x), 9.8)):
            return (28, 28, 30) if int(p.z * RES) % 3 else (40, 40, 42)
        return tone(FIXED["carpet"], 1 + p.n * 0.12)

    def m_headliner(self, p):
        return tone(FIXED["headliner"], 0.85 + p.n * 0.05)

    def m_visor(self, p):
        if p.face == "bottom" and abs(p.u - p.w / 2) < 1.6 and 0.8 < p.v < 2.6:
            return (176, 180, 186)                      # vanity mirror
        return tone(FIXED["visor"], (0.8 if p.face == "bottom" else 1) + p.n * 0.05)

    def m_door_inner(self, p):
        top = CAR.belt
        if top - 3.2 < p.y < top - 2.4:
            return tone(FIXED["plastic"], 1.25 + p.n * 0.05)          # armrest
        if top - 2.2 < p.y < top - 0.6:
            return self.m_seat(p)                                      # fabric insert
        front = CAR.doors[0]
        if abs(p.z - (front - 3)) < 0.7 and abs(p.y - (top - 1.4)) < 0.3:
            return tone(FIXED["chrome"], 0.9)                          # handle
        d = math.hypot(p.z - (front - 2.6), p.y - 6.6)
        if d < 1.3:
            return (24, 24, 26) if (int(p.u * RES) + int(p.v * RES)) % 2 else (54, 54, 56)  # speaker
        return self.flat(p, FIXED["plastic"])

    def m_seat(self, p):
        cx = p.box.origin[0] + p.box.size[0] / 2
        dx = abs(p.x - cx)
        c = FIXED["seat"]
        if dx > p.box.size[0] / 2 - 1.3:
            return tone(c, 0.82 + p.n * 0.04)                       # bolsters
        if fine_line(dx, p.box.size[0] / 2 - 1.4) and int((p.u + p.v) * RES) % 2:
            return (110, 108, 112)                                 # stitching
        rib = int((p.v if p.face in ("left", "right", "front", "back") else p.v) * RES) % 4 == 0
        return tone(c, (0.85 if rib else 1.05) + p.n * 0.07)

    def m_headrest(self, p):
        return tone(FIXED["seat"], 0.95 + p.n * 0.05)

    def m_console(self, p):
        if abs(p.x) < 0.9 and fine_line(p.z % 3, 1.5):
            return (20, 20, 20)
        return self.flat(p, FIXED["plastic"])

    def m_dash_top(self, p):
        if p.face == "top" and int(p.x * RES) % 3 == 0 and p.z > p.box.origin[2] + p.box.size[2] - 1.2:
            return (16, 16, 18)                         # demister slots
        return tone((36, 36, 38), 1 + p.n * 0.1)

    def m_dash_face(self, p):
        """The dashboard facing you: vents, the radio, the hazard button, the glovebox."""
        top = p.box.origin[1] + p.box.size[1]
        hw = CAR.hw
        x = p.x
        vent_y = top - 1.4 if p.box.origin[1] >= CAR.belt - 0.01 else None
        if vent_y is not None:
            for vx in (-hw + 4.2, -1.6, 1.6, hw - 4.2):
                if abs(x - vx) < 1.4 and abs(p.y - vent_y) < 0.8:
                    if abs(x - vx) > 1.15 or abs(p.y - vent_y) > 0.6:
                        return tone(FIXED["chrome"], 0.75)
                    return (18, 18, 20) if int(p.v * RES) % 2 else (44, 44, 46)
        else:
            # The radio and the hazard switch in the middle, the glovebox on the passenger side.
            if abs(x) < 3 and CAR.belt - 3.6 < p.y < CAR.belt - 0.6:
                if abs(x) < 2.6 and CAR.belt - 1.9 < p.y < CAR.belt - 0.9:
                    lit = int((x + 3) * RES) % 4 < 2 and abs(p.y - (CAR.belt - 1.4)) < 0.2
                    return (120, 220, 200) if lit else (20, 40, 46)
                if CAR.belt - 3.3 < p.y < CAR.belt - 2.5 and int((x + 3) * 2) % 2 == 0:
                    return (130, 130, 134)
                return (28, 28, 30)
            if abs(x) < 0.6 and abs(p.y - (CAR.belt - 4.3)) < 0.45:
                return (200, 40, 34) if abs(x) < 0.35 else (30, 30, 30)
            if 4 < x < hw - 3.5 and 6.4 < p.y < CAR.belt - 1:
                if fine_line(x, 4.2) or fine_line(x, hw - 3.7) or fine_line(p.y, 6.6) or fine_line(p.y, CAR.belt - 1.2):
                    return (20, 20, 22)
                if abs(x - (4 + hw - 3.5) / 2) < 1 and abs(p.y - (CAR.belt - 2)) < 0.25:
                    return tone(FIXED["chrome"], 0.85)
        return self.flat(p, FIXED["plastic"])

    def dial(self, d, a, r, red_zone=False):
        if d > r - 0.12:
            return tone(FIXED["chrome"], 0.95)
        sweep = 2.3
        if abs(a) <= sweep + 0.05 and d > r * 0.6:
            step = (a + sweep) / (2 * sweep) * 10
            if abs(step - round(step)) < 0.12 * (r / max(d, 0.1)):
                if red_zone and step > 8.5:
                    return (220, 40, 30)
                return (236, 236, 236)
        if d < 0.18:
            return (60, 60, 60)
        return (10, 10, 12)

    def m_cluster(self, p):
        """The instrument cluster: speedometer left, fuel right (their needles are parts), a display between."""
        cx = p.w / 2
        cy = p.h / 2 - 0.2
        for side, red in ((-1, False), (1, True)):
            d, a = disc(p, cx + side * 1.85 * -1, cy + 0.35)
            if d < 1.35:
                return self.dial(d, a, 1.35, red_zone=red)
        if abs(p.u - cx) < 0.45 and abs(p.v - (cy + 0.4)) < 0.7:
            return (200, 120, 30) if int(p.v * RES) % 2 and int(p.u * RES) % 2 else (30, 18, 8)
        if abs(p.v - (p.h - 0.35)) < 0.15 and int(p.u * RES) % 5 == 0:
            return self.rng.choice([(90, 60, 20), (20, 70, 30), (90, 20, 20)])
        return (16, 16, 18)

    def m_airbag(self, p):
        d, _ = disc(p, p.w / 2, p.h / 2)
        if d < 0.35:
            return tone(FIXED["chrome"], 0.9)
        return tone(FIXED["rubber"], 1.1 + p.n * 0.05)

    def m_wheel_rim(self, p):
        return tone(FIXED["rubber"], 1.0 + (0.25 if int((p.u + p.v) * RES) % 4 == 0 else 0) + p.n * 0.05)

    def m_ignition(self, p):
        d, a = disc(p, p.w / 2, p.h / 2)
        if d < 0.35:
            return (12, 12, 12) if abs(math.sin(a)) < 0.45 and d < 0.3 else (90, 90, 94)
        return tone(FIXED["chrome"], 1.1 - d * 0.3)

    def m_key(self, p):
        return tone(FIXED["key"], 1.0 + 0.15 * (p.v < 0.2) + p.n * 0.03)

    def m_fob_face(self, p):
        d, _ = disc(p, p.w / 2, p.h * 0.45)
        if d < 0.22:
            return (190, 40, 36)
        return tone(FIXED["fob"], 1.2)

    def m_chainring(self, p):
        d, a = disc(p, p.w / 2, p.h / 2)
        if d > 1.5:
            return None
        if d > 1.3:
            return (60, 60, 62) if int(a * 12) % 2 else (130, 130, 134)
        return tone(FIXED["metal"], 1.1 if (int(a / (2 * math.pi) * 5) % 2 and d > 0.5) else 0.6)

    # --- Wreck

    def wreck(self, c, p):
        """A burnt-out version of a texel."""
        if c is None:
            return None
        if isinstance(c, tuple) and c and c[0] == "P":
            k = (p.s + 1) / 2
            if p.n > 0.75:
                return (24, 22, 20, 255)
            if k > 0.6:
                return tone((128, 64, 30), 0.8 + p.n * 0.2)
            if k > 0.35:
                return tone((70, 52, 42), 0.85 + p.n * 0.15)
            return tone((46, 40, 36), 0.85 + p.n * 0.15)
        if len(c) > 3 and c[3] < 255:
            return None                                 # glass gone
        g = (c[0] + c[1] + c[2]) / 3
        burnt = mix(c[:3], (g, g, g), 0.6)
        f = 0.45 + 0.1 * p.n
        if p.n > 0.8:
            return (20, 18, 16, 255)
        return tone(burnt, f)


def face_regions(b):
    w, h, d = (math.ceil(v - 1e-6) for v in b.size)
    u, v = b.uv
    # Minecraft's box UV layout (see ModelPart.Cube).
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "left": (u, v + d, d, h), "back": (u + d, v + d, w, h),
        "right": (u + d + w, v + d, d, h), "front": (u + 2 * d + w, v + d, w, h),
    }


def face_size(b, face):
    sx, sy, sz = b.size
    return {"top": (sx, sz), "bottom": (sx, sz), "left": (sz, sy), "right": (sz, sy), "front": (sx, sy), "back": (sx, sy)}[face]


def position(b, face, u, v):
    """Where on the box (in the part's pixels) the texel at (u, v) of a face is."""
    x0, y0, z0 = b.origin
    x1, y1, z1 = x0 + b.size[0], y0 + b.size[1], z0 + b.size[2]
    if face == "top":
        return x0 + u, y1, z1 - v
    if face == "bottom":
        return x0 + u, y0, z1 - v
    if face == "left":
        return x0, y1 - v, z1 - u
    if face == "right":
        return x1, y1 - v, z0 + u
    if face == "front":
        return x1 - u, y1 - v, z1
    return x0 + u, y1 - v, z0


def smooth_noise(width, height, rng, cell=10):
    gw, gh = width // cell + 2, height // cell + 2
    grid = Image.new("L", (gw, gh))
    grid.putdata([rng.randrange(256) for _ in range(gw * gh)])
    big = grid.resize((gw * cell, gh * cell), Image.BICUBIC)
    data = big.load()
    return [[data[x, y] / 127.5 - 1 for x in range(width)] for y in range(height)]


def paint_model(name, parts, width, height):
    w, h = width * RES, height * RES
    rng = random.Random(name)
    painter = Painter(name, rng)
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    paint = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    wreck = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bp, pp, wp = base.load(), paint.load(), wreck.load()
    smooth = smooth_noise(w, h, rng)
    p = Px()
    for part in parts:
        for b in part.all_boxes():
            p.box = b
            for face, (fx, fy, fw, fh) in face_regions(b).items():
                spec = b.face(face)
                if spec is None:
                    continue
                p.face = face
                p.w, p.h = face_size(b, face)
                for j in range(fh * RES):
                    for i in range(fw * RES):
                        px, py = fx * RES + i, fy * RES + j
                        if px >= w or py >= h:
                            continue
                        p.u, p.v = (i + 0.5) / RES, (j + 0.5) / RES
                        p.x, p.y, p.z = position(b, face, p.u, p.v)
                        p.n = rng.random() * 2 - 1
                        p.s = smooth[py][px]
                        c = painter.shade(p, spec)
                        if c is None:
                            continue
                        if c[0] == "P":
                            g = clamp(int(c[1] * PAINT_GREY))
                            pp[px, py] = (g, g, g, 255)
                        else:
                            bp[px, py] = c if len(c) == 4 else c + (255,)
                        wc = painter.wreck(c, p)
                        if wc is not None:
                            wp[px, py] = wc if len(wc) == 4 else wc + (255,)
    return base, paint, wreck


def tint(color):
    r, g, b = (min(255, round(v * TINT_BOOST)) for v in color)
    return (0xFF << 24) | (r << 16) | (g << 8) | b


def main():
    for name, (build, width) in MODELS.items():
        parts = build()
        height = pack(parts, width)
        # As Java ints (ARGB, signed).
        tints = [tint(COLORS[c]) - (1 << 32) for c in VARIANTS[name]]
        model = {"texture": [width, height], "tints": tints, "parts": [p.to_json() for p in parts]}
        out = ROOT / "vehicles" / f"{name}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(model, indent="\t") + "\n")
        print("wrote", out.relative_to(ROOT.parents[3]), f"(texture {width}x{height} at {RES}x)")
        folder = ROOT / "textures/entity/vehicle" / name
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.png"):
            old.unlink()
        for image, file in zip(paint_model(name, parts, width, height), ("base", "paint", "wreck")):
            image.save(folder / f"{file}.png", optimize=True)
        print("  textures: base, paint, wreck")


if __name__ == "__main__":
    main()
