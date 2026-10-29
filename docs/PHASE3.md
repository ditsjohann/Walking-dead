# Phase 3: scavenging, barricades and weapons

![Barricades, weapons, heavy load](screenshots/phase3-barricades.png)
![A worn bag, opened with B](screenshots/phase3-bag.png)

## Loot
- **What gets loot:** any chest or barrel that a player didn't place fills up the **first time anyone opens it**.
  - Containers with a vanilla loot table (dungeon chests and so on) get outbreak loot instead.
  - Loot never comes back.
- **The room decides what's inside.** The blocks within 4 blocks of the container are counted:

  | Room | Recognised by | Typical loot |
  |---|---|---|
  | Kitchen | furnace, smoker, cauldron, campfire | bread, potatoes, water, sugar, bowls, kitchen knife, disinfectant |
  | Bedroom | beds | wool, rags, leather, books, school bag, hiking bag (rare), bandages |
  | Workshop | crafting table, anvil, grindstone, stonecutter | hammer, nails, iron, crowbar, machete (rare), planks, sticks |
  | Study | bookshelves, lecterns | books, paper, candles |
  | Household | anything else | a bit of everything |

  Many rolls come up empty: scarcity is the point.
- **Worn tools:** tools and weapons are found already worn (25–80% durability).
- **Walkers' pockets:** about 1 in 3 walkers drops something: rags, bread, a water bottle, a bandage, nails.
- **Your own containers** (the ones you place) never fill with loot.

## Weapons
They all wear out. The damage shown in game is per hit; a headshot does 4×.

| Weapon | Damage | Speed (hits/s) | Durability | Headshot | Where |
|---|---|---|---|---|---|
| Kitchen knife | 4 | 2.4 | 120 | 16: two stabs | kitchens; iron + stick |
| Baseball bat | 6 | 1.0 | 200 | 24: one swing | houses, bedrooms; 2 planks + stick |
| Crowbar | 6 | 1.2 | 600 | 24 | workshops |
| Machete | 7 | 1.4 | 250 | 28 | workshops (rare) |
| Hammer | 4 | 1.2 | 300 | 16 | workshops; 2 iron + 2 sticks |
| Iron axe | 9 | 0.9 | 250 | 36 | starter kit, workshops |

## Barricades
- **Nailing up:** right-click a window or door with a **hammer** to nail a plank in front of it.
  - Each plank costs 1 plank (any wood) and 2 nails.
  - Up to 4 planks per barricade.
- **Removing:** sneak-right-click with the hammer to pry a plank off. You get the plank back, but not the nails.
- **Noise:** hammering is **loud** (20 blocks). Walkers come to see what that was.
- **Blocking:** a barricade blocks the whole opening for movement. Nothing squeezes past even one plank.
  It stays up when the glass behind it is smashed.
- **Walkers smash in:** when a walker can't reach what it's after (you, or a noise), it batters what's in the way.
  - A window breaks after 3 blows.
  - A barricade loses a plank every 6 blows (about 24 seconds for all 4).
  - Every blow is noise that draws **other** walkers.
  - Once a walker has started, it keeps going until the barricade gives.
- **Nails:** 1 iron ingot gives 8 nails. You also find them in workshops and in walkers' pockets.

## Carry weight and bags
- **Weight:** you can carry **12 kg** comfortably. Every item weighs something:

  | Item | Weight |
  |---|---|
  | Blocks | 0.15 kg each (a stack of 64 = 9.6 kg) |
  | Food | 0.15 kg |
  | Tools and weapons | 0.4–2 kg |
  | Water bottle | 0.5 kg |
  | Rags and nails | almost nothing |

- **Heavy load moodle:** over 100% / 125% / 150% / 175% of capacity.
  - From level 2 on you're slowed down, more as it gets worse.
  - You also tire faster at every level.
- **Bags:**
  - **Right-click** a bag to open it. **Sneak-right-click** puts it on your back.
  - **B** opens the bag on your back.
  - A worn bag makes its contents lighter; a bag carried in your hands or inventory doesn't.

  | Bag | Storage | Contents weigh, when worn | Where |
  |---|---|---|---|
  | School bag | 18 slots | 70% | houses, bedrooms; 5 leather + 2 string |
  | Hiking bag | 27 slots | 40% | bedrooms (rare) |

- **No bags inside bags.** Contents stay inside a bag when you drop it.
- **The health panel (H)** shows how much you're carrying.

## Not in this phase yet
- **Crafting:** still the vanilla crafting table with our recipes. A PZ-style crafting menu comes later.
- **Room types:** guessed from nearby blocks for now. Phase 7 reads the real building types from
  OpenStreetMap (supermarket, pharmacy, police station...).

## Tests
- Server: `python3 tools/servertest/driver.py . tools/servertest/phase3.txt run/phase3.log`
- Client screenshots: `SDL_VIDEO_FORCE_EGL=1 xvfb-run -a ./gradlew runClientGameTest`
- Loot tables are generated: `python3 tools/data/make_loot_tables.py`
