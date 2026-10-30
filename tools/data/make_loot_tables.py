"""Generates the outbreak loot tables (26.x loot table format) from the compact lists below.

Run from the repo root:  python3 tools/data/make_loot_tables.py
Each entry is (item, weight, min, max), or (item, weight, min, max, extra) where extra is "worn"
(random durability), "water" (a water bottle), "fuel:<litres>" (a jerrycan) or "charge:<0-1>" (a battery). "empty" is a roll that finds nothing: scarcity.
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
        ("outbreak:portable_radio", 1, 1, 1, "worn"), ("empty", 10),
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
        ("outbreak:batteries", 2, 1, 2), ("outbreak:portable_radio", 1, 1, 1, "worn"), ("empty", 10),
    ]),
    # The trunk of an abandoned car: whatever someone packed to get out of town.
    "trunk": ((1, 4), [
        ("outbreak:jerrycan", 3, 1, 1), ("outbreak:jerrycan", 2, 1, 1, "fuel:6"), ("outbreak:tire", 2, 1, 1, "worn"),
        ("outbreak:jack", 2, 1, 1), ("outbreak:wrench", 1, 1, 1, "worn"), ("outbreak:engine_parts", 1, 1, 1),
        ("outbreak:bandage", 2, 1, 2), ("outbreak:rag", 3, 1, 3), ("minecraft:potion", 3, 1, 2, "water"),
        ("minecraft:bread", 2, 1, 2), ("minecraft:white_wool", 2, 1, 2), ("outbreak:hiking_bag", 1, 1, 1),
        ("outbreak:school_bag", 1, 1, 1), ("outbreak:baseball_bat", 1, 1, 1, "worn"), ("outbreak:portable_radio", 1, 1, 1, "worn"),
        ("outbreak:batteries", 1, 1, 2), ("empty", 12),
    ]),
}

# What walkers have in their pockets.
WALKER = [
    ("outbreak:rag", 15, 1, 2), ("minecraft:bread", 4, 1, 1), ("minecraft:potion", 3, 1, 1, "water"),
    ("outbreak:bandage", 2, 1, 1), ("outbreak:nails", 3, 1, 3), ("minecraft:iron_nugget", 2, 1, 2),
    ("minecraft:paper", 3, 1, 1), ("empty", 68),
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
