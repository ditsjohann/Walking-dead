"""Generates the outbreak loot tables (26.x loot table format) from the compact lists below.

Run from the repo root:  python3 tools/data/make_loot_tables.py
Each entry is (item, weight, min, max), or (item, weight, min, max, extra) where extra is "worn"
(random durability), "water" (a water bottle), "fuel:<litres>" (a jerrycan), "charge:<0-1>" (a battery) or
"rounds:<n>" (a gun with that many loaded). "empty" is a roll that finds nothing: scarcity.
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/data/outbreak/loot_table"

CONTAINERS = {
    # Default for any house container.
    "household": ((1, 4), [
        ("minecraft:bread", 6, 1, 2), ("minecraft:apple", 5, 1, 3), ("minecraft:potato", 5, 1, 3),
        ("minecraft:carrot", 4, 1, 3), ("minecraft:cookie", 3, 1, 4), ("minecraft:potion", 4, 1, 1, "water"),
        ("minecraft:glass_bottle", 4, 1, 2), ("outbreak:rag", 5, 1, 3), ("minecraft:string", 3, 1, 3),
        ("minecraft:paper", 3, 1, 3), ("minecraft:book", 2, 1, 1), ("minecraft:candle", 3, 1, 2),
        ("minecraft:torch", 3, 1, 4), ("minecraft:white_wool", 3, 1, 2), ("outbreak:nails", 2, 2, 6),
        ("outbreak:bandage", 1, 1, 1), ("outbreak:disinfectant", 1, 1, 1), ("outbreak:kitchen_knife", 1, 1, 1, "worn"),
        ("outbreak:baseball_bat", 1, 1, 1, "worn"), ("outbreak:school_bag", 1, 1, 1), ("outbreak:batteries", 2, 1, 2),
        ("outbreak:portable_radio", 1, 1, 1, "worn"), ("empty", 15),
    ]),
    # Near furnaces, smokers, cauldrons: food, drink and knives.
    "kitchen": ((2, 5), [
        ("minecraft:bread", 6, 1, 3), ("minecraft:baked_potato", 3, 1, 3), ("minecraft:potato", 6, 1, 4),
        ("minecraft:carrot", 5, 1, 4), ("minecraft:apple", 5, 1, 3), ("minecraft:beetroot", 3, 1, 4),
        ("minecraft:cookie", 3, 1, 6), ("minecraft:sugar", 4, 1, 3), ("minecraft:milk_bucket", 1, 1, 1),
        ("minecraft:potion", 5, 1, 1, "water"), ("outbreak:boiled_water", 2, 1, 1), ("minecraft:glass_bottle", 5, 1, 3),
        ("minecraft:bowl", 3, 1, 2), ("minecraft:mushroom_stew", 1, 1, 1), ("minecraft:cooked_chicken", 1, 1, 2),
        ("outbreak:kitchen_knife", 3, 1, 1, "worn"), ("outbreak:disinfectant", 2, 1, 1), ("empty", 10),
    ]),
    # Near beds: clothes (rags), bags, a bit of first aid.
    "bedroom": ((1, 4), [
        ("minecraft:white_wool", 4, 1, 2), ("minecraft:light_gray_wool", 3, 1, 2), ("outbreak:rag", 6, 1, 4),
        ("minecraft:leather", 3, 1, 2), ("minecraft:string", 3, 1, 3), ("minecraft:book", 3, 1, 1),
        ("minecraft:paper", 2, 1, 3), ("outbreak:school_bag", 2, 1, 1), ("outbreak:hiking_bag", 1, 1, 1),
        ("outbreak:bandage", 2, 1, 2), ("minecraft:candle", 2, 1, 2), ("outbreak:baseball_bat", 1, 1, 1, "worn"),
        ("empty", 14),
    ]),
    # Near crafting tables, anvils, grindstones: tools, nails, metal.
    "workshop": ((2, 5), [
        ("outbreak:hammer", 3, 1, 1, "worn"), ("outbreak:nails", 6, 4, 12), ("minecraft:iron_ingot", 3, 1, 2),
        ("minecraft:iron_nugget", 4, 2, 6), ("outbreak:crowbar", 2, 1, 1, "worn"), ("outbreak:machete", 1, 1, 1, "worn"),
        ("minecraft:iron_axe", 1, 1, 1, "worn"), ("minecraft:stone_pickaxe", 1, 1, 1, "worn"), ("minecraft:stick", 4, 2, 6),
        ("minecraft:oak_planks", 4, 2, 6), ("minecraft:string", 3, 1, 4), ("minecraft:iron_chain", 2, 1, 2),
        ("minecraft:torch", 3, 2, 6), ("minecraft:leather", 1, 1, 2), ("outbreak:wrench", 2, 1, 1, "worn"),
        ("outbreak:screwdriver", 2, 1, 1, "worn"), ("outbreak:jack", 1, 1, 1), ("outbreak:hose", 1, 1, 1, "worn"),
        ("outbreak:jerrycan", 2, 1, 1), ("outbreak:jerrycan", 1, 1, 1, "fuel:8"), ("outbreak:engine_parts", 1, 1, 2),
        ("outbreak:car_battery", 1, 1, 1, "charge:0.5"), ("outbreak:bike_tire", 1, 1, 1, "worn"), ("outbreak:batteries", 2, 1, 3),
        ("outbreak:portable_radio", 1, 1, 1, "worn"), ("outbreak:rifle_rounds", 1, 2, 6),
        ("outbreak:hunting_rifle", 1, 1, 1, "worn", "rounds:0"), ("empty", 12),
    ]),
    # Near bookshelves and lecterns.
    "study": ((1, 3), [
        ("minecraft:book", 6, 1, 3), ("minecraft:paper", 6, 1, 4), ("minecraft:writable_book", 2, 1, 1),
        ("minecraft:feather", 2, 1, 2), ("minecraft:ink_sac", 2, 1, 1), ("minecraft:candle", 3, 1, 2),
        ("minecraft:glass_bottle", 2, 1, 1), ("outbreak:disinfectant", 1, 1, 1), ("empty", 10),
    ]),
    # Our own containers: loot by what they are.
    "fridge": ((2, 5), [
        ("minecraft:milk_bucket", 2, 1, 1), ("minecraft:cooked_chicken", 3, 1, 2), ("minecraft:cooked_beef", 2, 1, 2),
        ("minecraft:apple", 5, 1, 3), ("minecraft:carrot", 5, 1, 4), ("minecraft:melon_slice", 4, 1, 4),
        ("minecraft:potion", 5, 1, 2, "water"), ("outbreak:boiled_water", 4, 1, 2), ("minecraft:cake", 1, 1, 1),
        ("minecraft:rotten_flesh", 4, 1, 3), ("empty", 8),
    ]),
    "kitchen_cupboard": ((1, 4), [
        ("minecraft:bread", 5, 1, 2), ("minecraft:sugar", 5, 1, 4), ("minecraft:cookie", 4, 1, 6),
        ("minecraft:bowl", 4, 1, 3), ("minecraft:glass_bottle", 5, 1, 3), ("minecraft:wheat", 3, 1, 4),
        ("outbreak:kitchen_knife", 2, 1, 1, "worn"), ("outbreak:disinfectant", 2, 1, 1), ("outbreak:rag", 3, 1, 3),
        ("empty", 10),
    ]),
    "toolbox": ((2, 4), [
        ("outbreak:hammer", 4, 1, 1, "worn"), ("outbreak:nails", 8, 4, 16), ("outbreak:crowbar", 2, 1, 1, "worn"),
        ("minecraft:iron_nugget", 5, 2, 8), ("minecraft:iron_ingot", 2, 1, 2), ("minecraft:string", 3, 1, 4),
        ("outbreak:wrench", 3, 1, 1, "worn"), ("outbreak:screwdriver", 3, 1, 1, "worn"), ("outbreak:engine_parts", 2, 1, 2),
        ("empty", 6),
    ]),
    "metal_cabinet": ((1, 4), [
        ("outbreak:nails", 4, 2, 10), ("minecraft:iron_ingot", 2, 1, 3), ("minecraft:paper", 5, 1, 6),
        ("minecraft:book", 3, 1, 2), ("outbreak:bandage", 2, 1, 2), ("outbreak:disinfectant", 2, 1, 1),
        ("minecraft:torch", 3, 2, 6), ("outbreak:machete", 1, 1, 1, "worn"), ("outbreak:hiking_bag", 1, 1, 1),
        ("outbreak:batteries", 2, 1, 2), ("outbreak:portable_radio", 1, 1, 1, "worn"), ("outbreak:ammo_9mm", 1, 4, 12),
        ("outbreak:pistol", 1, 1, 1, "worn", "rounds:6"), ("empty", 12),
    ]),
    # The trunk of an abandoned car: whatever someone packed to get out of town.
    "trunk": ((1, 4), [
        ("outbreak:jerrycan", 3, 1, 1), ("outbreak:jerrycan", 2, 1, 1, "fuel:6"), ("outbreak:tire", 2, 1, 1, "worn"),
        ("outbreak:jack", 2, 1, 1), ("outbreak:wrench", 1, 1, 1, "worn"), ("outbreak:engine_parts", 1, 1, 1),
        ("outbreak:bandage", 2, 1, 2), ("outbreak:rag", 3, 1, 3), ("minecraft:potion", 3, 1, 2, "water"),
        ("minecraft:bread", 2, 1, 2), ("minecraft:white_wool", 2, 1, 2), ("outbreak:hiking_bag", 1, 1, 1),
        ("outbreak:school_bag", 1, 1, 1), ("outbreak:baseball_bat", 1, 1, 1, "worn"), ("outbreak:portable_radio", 1, 1, 1, "worn"),
        ("outbreak:batteries", 1, 1, 2), ("outbreak:shotgun_shells", 1, 2, 6), ("outbreak:shotgun", 1, 1, 1, "worn", "rounds:2"),
        ("empty", 14),
    ]),
}

# By building (phase 7, on the Groningen map: see Places.java): "place_<kind>".
FOOD = [("minecraft:bread", 5, 1, 3), ("minecraft:apple", 4, 1, 3), ("minecraft:cookie", 3, 1, 4), ("minecraft:potato", 3, 1, 3)]
PLACES = {
    "military": ((2, 5), [
        ("outbreak:rifle_rounds", 6, 3, 10), ("outbreak:ammo_9mm", 6, 5, 15), ("outbreak:hunting_rifle", 1, 1, 1, "worn", "rounds:2"),
        ("outbreak:pistol", 2, 1, 1, "worn", "rounds:5"), ("outbreak:shotgun_shells", 3, 2, 8), ("outbreak:bandage", 5, 1, 3),
        ("outbreak:disinfectant", 3, 1, 2), ("outbreak:hiking_bag", 2, 1, 1), ("outbreak:machete", 2, 1, 1, "worn"),
        ("outbreak:portable_radio", 2, 1, 1, "worn"), ("outbreak:batteries", 3, 1, 3), ("outbreak:jerrycan", 2, 1, 1, "fuel:12"),
        ("minecraft:cooked_beef", 3, 1, 3), ("empty", 6),
    ]),
    "hospital": ((2, 5), [
        ("outbreak:bandage", 8, 1, 4), ("outbreak:disinfectant", 6, 1, 2), ("outbreak:rag", 5, 2, 5),
        ("outbreak:boiled_water", 3, 1, 2), ("minecraft:glass_bottle", 3, 1, 3), ("minecraft:paper", 3, 1, 4),
        ("minecraft:honey_bottle", 1, 1, 1), ("outbreak:kitchen_knife", 1, 1, 1, "worn"), ("empty", 8),
    ]),
    "clinic": ((1, 4), [
        ("outbreak:bandage", 6, 1, 3), ("outbreak:disinfectant", 5, 1, 2), ("outbreak:rag", 4, 1, 4),
        ("minecraft:paper", 4, 1, 4), ("minecraft:book", 1, 1, 1), ("empty", 10),
    ]),
    "pharmacy": ((2, 5), [
        ("outbreak:disinfectant", 8, 1, 3), ("outbreak:bandage", 8, 1, 4), ("outbreak:rag", 3, 1, 3),
        ("minecraft:honey_bottle", 2, 1, 2), ("outbreak:boiled_water", 2, 1, 2), ("outbreak:batteries", 2, 1, 2), ("empty", 6),
    ]),
    "police": ((2, 4), [
        ("outbreak:ammo_9mm", 7, 4, 12), ("outbreak:pistol", 2, 1, 1, "worn", "rounds:8"), ("outbreak:baton", 4, 1, 1, "worn"),
        ("outbreak:shotgun", 1, 1, 1, "worn", "rounds:3"), ("outbreak:shotgun_shells", 3, 2, 8), ("outbreak:bandage", 3, 1, 2),
        ("outbreak:portable_radio", 3, 1, 1, "worn"), ("outbreak:batteries", 3, 1, 3), ("minecraft:paper", 3, 1, 4), ("empty", 8),
    ]),
    "fire_station": ((2, 4), [
        ("outbreak:crowbar", 3, 1, 1, "worn"), ("minecraft:iron_axe", 3, 1, 1, "worn"), ("outbreak:bandage", 4, 1, 3),
        ("outbreak:disinfectant", 2, 1, 1), ("outbreak:jerrycan", 2, 1, 1, "fuel:10"), ("outbreak:hose", 2, 1, 1),
        ("outbreak:portable_radio", 2, 1, 1, "worn"), ("outbreak:hiking_bag", 1, 1, 1), ("empty", 8),
    ]),
    "fuel": ((2, 5), [
        ("outbreak:jerrycan", 4, 1, 1), ("outbreak:jerrycan", 2, 1, 1, "fuel:15"), ("outbreak:car_battery", 2, 1, 1, "charge:0.9"),
        ("outbreak:batteries", 3, 1, 3), ("minecraft:cookie", 5, 1, 6), ("minecraft:bread", 4, 1, 2),
        ("minecraft:potion", 5, 1, 2, "water"), ("outbreak:hose", 2, 1, 1), ("outbreak:tire", 1, 1, 1), ("empty", 6),
    ]),
    "supermarket": ((3, 6), FOOD + [
        ("minecraft:carrot", 4, 1, 4), ("minecraft:beetroot", 3, 1, 4), ("minecraft:melon_slice", 3, 1, 4),
        ("minecraft:sugar", 3, 1, 3), ("minecraft:potion", 5, 1, 3, "water"), ("minecraft:cooked_chicken", 2, 1, 2),
        ("minecraft:cooked_porkchop", 1, 1, 2), ("minecraft:baked_potato", 3, 1, 3), ("minecraft:milk_bucket", 1, 1, 1),
        ("outbreak:batteries", 2, 1, 2), ("outbreak:rag", 2, 1, 3), ("minecraft:rotten_flesh", 3, 1, 3), ("empty", 6),
    ]),
    "grocery": ((2, 5), FOOD + [
        ("minecraft:carrot", 3, 1, 3), ("minecraft:cake", 1, 1, 1), ("minecraft:pumpkin_pie", 2, 1, 2),
        ("minecraft:potion", 3, 1, 2, "water"), ("minecraft:rotten_flesh", 3, 1, 3), ("empty", 6),
    ]),
    "hardware": ((2, 5), [
        ("outbreak:hammer", 4, 1, 1), ("outbreak:nails", 8, 6, 20), ("outbreak:crowbar", 3, 1, 1), ("minecraft:iron_axe", 2, 1, 1),
        ("outbreak:wrench", 3, 1, 1), ("outbreak:screwdriver", 3, 1, 1), ("minecraft:oak_planks", 5, 4, 12),
        ("outbreak:jerrycan", 2, 1, 1), ("outbreak:batteries", 3, 1, 4), ("minecraft:iron_chain", 2, 1, 3),
        ("outbreak:hose", 2, 1, 1), ("minecraft:torch", 3, 2, 6), ("empty", 5),
    ]),
    "bike_shop": ((2, 4), [
        ("outbreak:bike_tire", 6, 1, 2), ("outbreak:bicycle", 1, 1, 1), ("outbreak:wrench", 3, 1, 1),
        ("outbreak:screwdriver", 2, 1, 1), ("outbreak:batteries", 2, 1, 2), ("empty", 6),
    ]),
    "car_shop": ((2, 4), [
        ("outbreak:tire", 5, 1, 1), ("outbreak:car_battery", 3, 1, 1, "charge:0.8"), ("outbreak:engine_parts", 5, 1, 3),
        ("outbreak:jack", 3, 1, 1), ("outbreak:wrench", 3, 1, 1), ("outbreak:jerrycan", 3, 1, 1), ("outbreak:hose", 2, 1, 1),
        ("empty", 5),
    ]),
    "clothes": ((1, 4), [
        ("minecraft:white_wool", 4, 1, 3), ("minecraft:black_wool", 3, 1, 3), ("minecraft:leather", 4, 1, 3),
        ("outbreak:rag", 6, 2, 5), ("outbreak:school_bag", 2, 1, 1), ("outbreak:hiking_bag", 2, 1, 1),
        ("minecraft:leather_boots", 2, 1, 1), ("minecraft:leather_chestplate", 1, 1, 1), ("empty", 8),
    ]),
    "electronics": ((1, 4), [
        ("outbreak:batteries", 7, 2, 6), ("outbreak:portable_radio", 4, 1, 1), ("minecraft:redstone", 3, 1, 4),
        ("minecraft:copper_ingot", 2, 1, 2), ("empty", 8),
    ]),
    "books": ((1, 4), [
        ("minecraft:book", 8, 1, 4), ("minecraft:paper", 6, 2, 8), ("minecraft:writable_book", 2, 1, 1),
        ("minecraft:map", 2, 1, 1), ("minecraft:candle", 2, 1, 2), ("empty", 8),
    ]),
    "restaurant": ((2, 5), [
        ("minecraft:bread", 4, 1, 3), ("minecraft:cooked_chicken", 3, 1, 3), ("minecraft:cooked_beef", 2, 1, 2),
        ("minecraft:baked_potato", 4, 1, 4), ("minecraft:mushroom_stew", 2, 1, 1), ("minecraft:potion", 4, 1, 2, "water"),
        ("outbreak:kitchen_knife", 4, 1, 1, "worn"), ("outbreak:machete", 1, 1, 1, "worn"), ("minecraft:bowl", 3, 1, 3),
        ("minecraft:rotten_flesh", 3, 1, 3), ("empty", 6),
    ]),
    "bar": ((1, 4), [
        ("minecraft:potion", 6, 1, 3, "water"), ("minecraft:honey_bottle", 2, 1, 2), ("minecraft:glass_bottle", 6, 1, 4),
        ("minecraft:cookie", 3, 1, 4), ("outbreak:baseball_bat", 1, 1, 1, "worn"), ("empty", 8),
    ]),
    "school": ((1, 4), [
        ("minecraft:paper", 6, 2, 8), ("minecraft:book", 5, 1, 3), ("outbreak:school_bag", 4, 1, 1), ("minecraft:apple", 3, 1, 2),
        ("minecraft:cookie", 3, 1, 3), ("outbreak:bandage", 2, 1, 1), ("outbreak:baseball_bat", 1, 1, 1, "worn"), ("empty", 8),
    ]),
    "church": ((1, 3), [
        ("minecraft:candle", 8, 1, 4), ("minecraft:book", 4, 1, 2), ("minecraft:paper", 3, 1, 3),
        ("minecraft:bread", 2, 1, 2), ("outbreak:rag", 3, 1, 3), ("empty", 10),
    ]),
    "hotel": ((1, 4), [
        ("minecraft:white_wool", 5, 1, 3), ("outbreak:rag", 4, 1, 3), ("minecraft:cookie", 3, 1, 3),
        ("minecraft:potion", 3, 1, 2, "water"), ("outbreak:hiking_bag", 2, 1, 1), ("outbreak:school_bag", 2, 1, 1),
        ("minecraft:paper", 3, 1, 3), ("empty", 10),
    ]),
    "gym": ((1, 4), [
        ("minecraft:potion", 6, 1, 3, "water"), ("outbreak:baseball_bat", 4, 1, 1, "worn"), ("outbreak:rag", 4, 1, 3),
        ("minecraft:apple", 3, 1, 2), ("outbreak:hiking_bag", 1, 1, 1), ("empty", 8),
    ]),
    "office": ((1, 4), [
        ("minecraft:paper", 8, 2, 8), ("minecraft:book", 3, 1, 2), ("outbreak:batteries", 3, 1, 2), ("minecraft:cookie", 3, 1, 4),
        ("minecraft:potion", 3, 1, 1, "water"), ("outbreak:screwdriver", 1, 1, 1, "worn"), ("outbreak:portable_radio", 1, 1, 1, "worn"),
        ("empty", 10),
    ]),
    "industrial": ((2, 5), [
        ("outbreak:nails", 6, 4, 16), ("minecraft:iron_ingot", 4, 1, 3), ("minecraft:iron_nugget", 4, 2, 8),
        ("outbreak:hammer", 2, 1, 1, "worn"), ("outbreak:crowbar", 2, 1, 1, "worn"), ("outbreak:wrench", 3, 1, 1, "worn"),
        ("outbreak:jerrycan", 2, 1, 1, "fuel:6"), ("outbreak:engine_parts", 2, 1, 2), ("outbreak:car_battery", 1, 1, 1, "charge:0.4"),
        ("minecraft:oak_planks", 4, 2, 8), ("empty", 6),
    ]),
    "store": ((1, 4), [
        ("minecraft:cookie", 4, 1, 4), ("minecraft:paper", 3, 1, 3), ("outbreak:batteries", 3, 1, 3), ("minecraft:candle", 3, 1, 2),
        ("outbreak:rag", 3, 1, 3), ("outbreak:school_bag", 1, 1, 1), ("minecraft:potion", 2, 1, 1, "water"), ("empty", 10),
    ]),
}

# What walkers have in their pockets.
WALKER = [
    ("outbreak:rag", 15, 1, 2), ("minecraft:bread", 4, 1, 1), ("minecraft:potion", 3, 1, 1, "water"),
    ("outbreak:bandage", 2, 1, 1), ("outbreak:nails", 3, 1, 3), ("minecraft:iron_nugget", 2, 1, 2),
    ("minecraft:paper", 3, 1, 1), ("outbreak:ammo_9mm", 1, 2, 7), ("empty", 68),
]


def entry(spec):
    if spec[0] == "empty":
        return {"type": "minecraft:empty", "weight": spec[1]}
    name, weight, lo, hi, *extra = spec
    modifiers = []
    if hi > 1:
        modifiers.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if "worn" in extra:
        modifiers.append({"type": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.25, "max": 0.8}})
    for e in extra:
        if e.startswith("fuel:") or e.startswith("charge:"):
            key, value = e.split(":")
            modifiers.append({"type": "minecraft:set_components", "components": {f"outbreak:{key}": float(value)}})
        if e.startswith("rounds:"):
            modifiers.append({"type": "minecraft:set_components", "components": {"outbreak:rounds": int(e.split(":")[1])}})
    if "water" in extra:
        modifiers.append({"type": "minecraft:set_potion", "id": "minecraft:water"})
    result = {"type": "minecraft:item", "name": name, "weight": weight}
    if modifiers:
        result["modifier"] = modifiers
    return result


def write(path, table):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(table, indent="\t") + "\n")
    print("wrote", path.relative_to(OUT.parents[3]))


def main():
    for name, ((lo, hi), entries) in CONTAINERS.items():
        write(OUT / "containers" / f"{name}.json", {"type": "minecraft:chest", "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": [entry(e) for e in entries]}]})
    for name, ((lo, hi), entries) in PLACES.items():
        write(OUT / "containers" / f"place_{name}.json", {"type": "minecraft:chest", "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": [entry(e) for e in entries]}]})
    write(OUT / "entities" / "walker.json", {"type": "minecraft:entity", "pools": [
        {"rolls": 1, "entries": [entry(e) for e in WALKER]}]})
    # A barricade drops one oak plank per plank still nailed on.
    write(OUT / "blocks" / "barricade.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:item", "name": "minecraft:oak_planks", "modifier": [
            {"type": "minecraft:set_count", "count": n, "condition": {
                "type": "minecraft:match_block", "blocks": "outbreak:barricade", "state": {"planks": str(n)}}}
            for n in range(2, 5)]}]}]})


if __name__ == "__main__":
    main()
