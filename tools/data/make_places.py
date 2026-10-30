"""Builds the map of what Groningen's buildings are, from OpenStreetMap (phase 7).

Run from the repo root:  python3 tools/data/make_places.py
Writes src/main/resources/data/outbreak/places/groningen.json: every shop, pharmacy, hospital,
school, police station, petrol station, office, church, factory, barracks... in the city, as a kind
and a box in latitude/longitude (the game projects it onto the map). A shop mapped as a point gets
the box of the building it stands in.

Queries an Overpass API server (overpass.kumi.systems by default; OVERPASS=<url> to use another).
The raw answers are cached in tools/data/cache/ (not committed), so a rerun works offline.
"""
import json
import math
import os
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/outbreak/places/groningen.json"
CACHE = Path(__file__).resolve().parent / "cache"
SERVERS = [os.environ["OVERPASS"]] if "OVERPASS" in os.environ else [
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]
# min lat, min lng, max lat, max lng: the Arnis box of the city (see docs/MAP_GRONINGEN.md).
BBOX = (53.17, 6.48, 53.26, 6.65)
# The box is fetched in TILES x TILES pieces (the public servers give up on the whole city at once).
TILES = 3

# What we make of the tags: (key, values or None for any, kind). The first that matches wins.
RULES = [
    ("landuse", {"military"}, "military"),
    ("military", None, "military"),
    ("amenity", {"hospital"}, "hospital"),
    ("building", {"hospital"}, "hospital"),
    ("amenity", {"clinic", "doctors", "dentist", "veterinary"}, "clinic"),
    ("healthcare", None, "clinic"),
    ("amenity", {"pharmacy"}, "pharmacy"),
    ("shop", {"chemist", "medical_supply"}, "pharmacy"),
    ("amenity", {"police"}, "police"),
    ("amenity", {"fire_station"}, "fire_station"),
    ("building", {"fire_station"}, "fire_station"),
    ("amenity", {"fuel"}, "fuel"),
    ("shop", {"supermarket"}, "supermarket"),
    ("shop", {"convenience", "bakery", "butcher", "greengrocer", "deli", "cheese", "seafood", "confectionery",
              "pastry", "kiosk", "alcohol", "beverages", "wine", "coffee", "tea", "chocolate", "frozen_food"}, "grocery"),
    ("shop", {"doityourself", "hardware", "trade", "garden_centre", "tools", "paint", "building_materials"}, "hardware"),
    ("shop", {"bicycle"}, "bike_shop"),
    ("shop", {"car", "car_repair", "car_parts", "tyres", "motorcycle"}, "car_shop"),
    ("amenity", {"car_repair"}, "car_shop"),
    ("shop", {"clothes", "shoes", "fashion", "boutique", "outdoor", "sports", "bag", "fabric"}, "clothes"),
    ("shop", {"electronics", "mobile_phone", "computer", "hifi", "appliance", "camera"}, "electronics"),
    ("shop", {"books", "stationery", "newsagent"}, "books"),
    ("amenity", {"library"}, "books"),
    ("amenity", {"restaurant", "fast_food", "cafe", "food_court", "ice_cream"}, "restaurant"),
    ("amenity", {"bar", "pub", "nightclub", "biergarten"}, "bar"),
    ("amenity", {"school", "kindergarten", "college", "university", "childcare"}, "school"),
    ("building", {"school", "university", "college", "kindergarten"}, "school"),
    ("amenity", {"place_of_worship"}, "church"),
    ("building", {"church", "chapel", "cathedral", "mosque", "synagogue"}, "church"),
    ("tourism", {"hotel", "hostel", "guest_house"}, "hotel"),
    ("building", {"hotel"}, "hotel"),
    ("leisure", {"sports_centre", "fitness_centre", "stadium", "sports_hall"}, "gym"),
    ("building", {"stadium", "sports_hall"}, "gym"),
    ("amenity", {"bank", "townhall", "courthouse", "post_office", "community_centre"}, "office"),
    ("office", None, "office"),
    ("building", {"office", "commercial", "government"}, "office"),
    ("building", {"industrial", "warehouse", "factory", "manufacture"}, "industrial"),
    ("landuse", {"industrial"}, "industrial"),
    ("shop", None, "store"),
    ("building", {"retail", "supermarket"}, "store"),
]

# One query per category (the servers time out on one big one).
QUERIES = {
    "shops": 'nwr["shop"]({b});',
    "amenities": 'nwr["amenity"~"^(pharmacy|hospital|clinic|doctors|dentist|veterinary|police|fire_station|fuel|car_repair|library|restaurant|fast_food|cafe|food_court|ice_cream|bar|pub|nightclub|biergarten|school|kindergarten|college|university|childcare|place_of_worship|bank|townhall|courthouse|post_office|community_centre)$"]({b});',
    "health_office": 'nwr["healthcare"]({b}); nwr["office"]({b}); nwr["tourism"~"^(hotel|hostel|guest_house)$"]({b}); nwr["leisure"~"^(sports_centre|fitness_centre|stadium|sports_hall)$"]({b});',
    "buildings": 'way["building"~"^(hospital|fire_station|school|university|college|kindergarten|church|chapel|cathedral|mosque|synagogue|hotel|stadium|sports_hall|office|commercial|government|industrial|warehouse|factory|manufacture|retail|supermarket)$"]({b});',
    "land": 'nwr["landuse"~"^(military|industrial)$"]({b}); nwr["military"]({b});',
}
WRAP = "[out:json][timeout:120];\n(\n{q}\n);\nout tags bb center qt;\n"

# The buildings around point POIs, to give each its building's box.
BUILDINGS = """
[out:json][timeout:120];
(
  node["shop"]({b});
  node["amenity"~"^(pharmacy|clinic|doctors|dentist|police|fire_station|library|restaurant|fast_food|cafe|bar|pub|school|bank|post_office)$"]({b});
  node["healthcare"]({b});
  node["office"]({b});
)->.p;
way(around.p:12)["building"];
out bb qt;
"""


def fetch(name, query):
    """Every element the query finds in the city, tile by tile (each tile cached on its own)."""
    seen = {}
    for i in range(TILES):
        for j in range(TILES):
            lat0 = BBOX[0] + (BBOX[2] - BBOX[0]) * i / TILES
            lng0 = BBOX[1] + (BBOX[3] - BBOX[1]) * j / TILES
            tile = (lat0, lng0, lat0 + (BBOX[2] - BBOX[0]) / TILES, lng0 + (BBOX[3] - BBOX[1]) / TILES)
            for element in fetch_tile(f"{name}_{i}{j}", query, tile)["elements"]:
                seen[(element["type"], element["id"])] = element
    print("  ", name, len(seen), "elements")
    return list(seen.values())


def fetch_tile(name, query, tile, depth=0):
    """One tile's answer; a tile the servers keep giving up on is fetched as four smaller ones."""
    CACHE.mkdir(exist_ok=True)
    cached = CACHE / f"{name}.json"
    if cached.exists():
        return json.loads(cached.read_text())
    b = ",".join(f"{v:.5f}" for v in tile)
    body = urllib.parse.urlencode({"data": query.replace("{b}", b)}).encode()
    error = None
    for attempt in range(4 if depth < 2 else 8):
        server = SERVERS[attempt % len(SERVERS)]
        try:
            request = urllib.request.Request(server, data=body, headers={"User-Agent": "outbreak-mod-map-builder"})
            with urllib.request.urlopen(request, timeout=200) as response:
                data = json.loads(response.read())
            if "remark" in data and not data.get("elements"):
                raise RuntimeError(data["remark"])
            cached.write_text(json.dumps(data))
            print("fetched", name, len(data.get("elements", [])), "from", server, flush=True)
            return data
        except Exception as e:  # noqa: BLE001 (any network trouble: try again on the other server)
            error = e
            print("  ", name, "failed on", server, ":", e, flush=True)
            time.sleep(10 * (attempt + 1))
    if depth >= 2:
        raise SystemExit(f"could not fetch {name}: {error}")
    mid_lat, mid_lng = (tile[0] + tile[2]) / 2, (tile[1] + tile[3]) / 2
    quarters = [(tile[0], tile[1], mid_lat, mid_lng), (tile[0], mid_lng, mid_lat, tile[3]),
                (mid_lat, tile[1], tile[2], mid_lng), (mid_lat, mid_lng, tile[2], tile[3])]
    elements = []
    for k, quarter in enumerate(quarters):
        elements += fetch_tile(f"{name}{'abcd'[k]}", query, quarter, depth + 1)["elements"]
    data = {"elements": elements}
    cached.write_text(json.dumps(data))
    return data


def kind_of(tags):
    for key, values, kind in RULES:
        value = tags.get(key)
        if value is not None and (values is None or value in values):
            return kind
    return None


def box_of(element):
    if "bounds" in element:
        b = element["bounds"]
        return (b["minlat"], b["minlon"], b["maxlat"], b["maxlon"])
    if "lat" in element:
        return (element["lat"], element["lon"], element["lat"], element["lon"])
    if "center" in element:
        c = element["center"]
        return (c["lat"], c["lon"], c["lat"], c["lon"])
    return None


def area(box):
    return (box[2] - box[0]) * (box[3] - box[1])


def main():
    places = []
    for name, query in QUERIES.items():
        places += fetch(name, WRAP.replace("{q}", query))
    buildings = [box_of(e) for e in fetch("buildings", BUILDINGS) if box_of(e)]
    # A degree of latitude is 111 km; of longitude here, 111 km x cos(53.2).
    pad_lat = 8 / 111000
    pad_lng = 8 / (111000 * math.cos(math.radians(53.2)))
    out = []
    kinds = {}
    for element in places:
        kind = kind_of(element.get("tags", {}))
        box = box_of(element)
        if kind is None or box is None:
            continue
        if area(box) == 0:
            # A point: the smallest building it stands in, or a small square round it.
            lat, lng = box[0], box[1]
            inside = [b for b in buildings if b[0] <= lat <= b[2] and b[1] <= lng <= b[3]]
            box = min(inside, key=area) if inside else (lat - pad_lat, lng - pad_lng, lat + pad_lat, lng + pad_lng)
        # Leave out whole districts (an industrial estate is its own buildings).
        if (box[2] - box[0]) * 111000 > 1500 or (box[3] - box[1]) * 111000 * 0.6 > 1500:
            continue
        out.append([kind] + [round(v, 6) for v in box])
        kinds[kind] = kinds.get(kind, 0) + 1
    out.sort()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"bbox": list(BBOX), "places": out}, separators=(",", ":")) + "\n")
    print("wrote", OUT.relative_to(ROOT), len(out), "places")
    for kind, n in sorted(kinds.items(), key=lambda kv: -kv[1]):
        print(f"  {kind}: {n}")


if __name__ == "__main__":
    main()
