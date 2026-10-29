# Design — Walking Dead × Project Zomboid in Groningen

Status: **draft v0.2: total conversion.** Phase 0 (project setup) is built; everything else is still open for discussion.

## 1. Pitch

The outbreak started in Groningen. You wake up a few days in, in a real 1:1
copy of the city generated from OpenStreetMap. The *feel* is Project Zomboid:
slow, systemic, punishing survival where every scratch matters and you will
eventually die. The *heart* is The Walking Dead: walkers are slow but endless,
everyone is already infected, and the real story is about the community you
build and the people you meet.

> "This is how you died" — but first, how long did you last?

## 2. Platform decision

**Fabric, Minecraft 26.3, Java 25.**

- Arnis officially targets Java 1.21.4+ and recommends the newest version.
  Its latest release (v3.3.0) includes fixes for 26.3. On 1.21.1 the world
  would need a hack, so we went with 26.3 instead.
- Every library we'd want has a 26.3 build: Fabric API, GeckoLib (animations),
  Sodium (only as a beta), Distant Horizons and Mod Menu.
- Since 26.1, Minecraft ships without obfuscation, so mod code uses the real
  Mojang names. That makes the game code easier to read and to update.
- Forge / NeoForge would bring nothing extra for our own code.

The mod's code does **not** depend on the map: it runs in any world. Players
get everything as **one package**: a Modrinth modpack (`.mrpack`) with the
mod, its performance companions (Sodium, Distant Horizons) and the Groningen
world. Later, an optional "map data" file (see §8) can give the mod extra
knowledge about Groningen.

## 3. Total conversion

This is not an add-on to Minecraft. **It replaces the game.** Everything a
player sees and does should belong to the outbreak. If a vanilla system
doesn't fit, it gets removed or replaced.

| Vanilla system | What happens |
|---|---|
| Title screen, world creation | **Replaced.** Our own menu: *New survivor* → pick a scenario (start location and day) → create a character → spawn in Groningen. |
| Character | **Replaced.** PZ-style **profession and traits**: nurse, police officer, student, carpenter, farmer; *Strong*, *Smoker*, *Short-sighted*... |
| Hearts, hunger, XP bar | **Replaced.** A health panel per body part, plus moodle icons. No XP orbs, no levels. |
| Inventory | **Replaced.** Weight-based, with bags/backpacks as equipment and containers you open and loot. |
| Crafting table, recipes | **Replaced.** All vanilla recipes are removed. Real-world recipes (barricade planks, bandages from torn clothes, molotovs) are made from the inventory or at a workbench, gated by skills. |
| Mining and building | **Mostly vanilla.** Players can break and place blocks freely. Because vanilla recipes and ores are gone, materials come from scavenging and taking things apart. Breaking blocks makes **noise**, which attracts walkers. Barricades are an extra layer on top. |
| Mobs | **Removed.** No creepers, skeletons, villagers or vanilla zombies. Only walkers, animals that fit (birds, deer, dogs, livestock) and later survivor NPCs. |
| Nether, End, portals, enchanting, potions, redstone toys | **Removed.** |
| Ores, diamonds, vanilla progression, advancements | **Removed.** Progress is surviving days, skills and your base. |
| Sleep | **Changed.** Sleeping is dangerous (walkers can find you) and fatigue is a need. No skipping nights in multiplayer. |
| Day/night, weather | **Kept and tuned.** A longer day; rain matters (wet, cold, it washes off walker guts). |
| Blocks, rendering, sound engine | **Kept.** This is our engine. Vanilla blocks are what Arnis builds Groningen out of. |
| Death | **Changed.** You turn into a walker, and you start a new character (see §6). |

**Technical approach:**
- Mostly data-driven where possible: recipe and loot overrides, tags, and
  spawn rules in a built-in data pack.
- Fabric API events and HUD layers.
- Mixins only where Minecraft has no hook (for example mining rules, the
  inventory, the title screen).
- Vanilla content is hidden from players, not deleted from the registries,
  so the game stays stable and other performance mods keep working.

## 4. Design pillars

1. **Every wound matters** (PZ). Health is a body, not a heart bar.
2. **Noise is death** (PZ + TWD). Everything you do makes sound, and walkers come to it.
3. **The dead are slow, the herd is not** (TWD). One walker is easy. Fifty are a death sentence.
4. **Everyone is infected** (TWD). Die for any reason and you turn.
5. **The world winds down** (PZ). Power, water and broadcasts stop. Loot never comes back.
6. **Real place, real knowledge** (ours). Locals know where the hospital, the
   bridges and the supermarkets are, and that knowledge is an advantage.

## 5. Walkers

- **Movement:** slow shamblers by default. A sandbox option (see §10) allows
  fast shamblers or sprinters.
- **Killing them:** body hits only knock them down or slow them. A **headshot**
  or a stomp on a downed walker kills.
- **Senses:**
  - *Hearing* is their main sense. Sound events have a radius, and walkers move
    to the last thing they heard.
  - *Sight* is a short cone that is weaker at night. **Sneaking** halves
    sound and sight.
- **Herds:** walkers drift and slowly migrate between neighbourhoods. A large
  noise, like a gunshot or a car alarm, pulls the whole herd.
- **Behaviour:** they bang on doors and break weak barricades. They don't burn
  in daylight. They can walk underwater along canal bottoms, so the canals
  are barriers, not walls.
- **Variety:** crawlers (legless), bloated/water walkers, and walkers in the
  uniforms of where they died (police, UMCG staff, students) with matching
  loot.
- **Cleanup:** all other hostile vanilla mobs are disabled (configurable).
- **Model:** vanilla zombie model at first, custom GeckoLib models later.

## 6. Survivor (the PZ layer)

- **Body-part injuries:** scratch, laceration, bite, deep wound, fracture, burn.
  Each part can bleed and needs a bandage. Dirty bandages cause wound infection.
- **Knox-style infection:**
  - bite = 100%
  - laceration ≈ 25%
  - scratch ≈ 7%
  - The infection is hidden at first. It shows up as fever, then you die and turn.
  - TWD twist: **amputating the limb quickly** can save you, at a heavy cost.
- **Moodles:** small status icons on the HUD for hunger, thirst, fatigue,
  panic, stress, boredom, wet, cold/hot, pain, bleeding, sick and heavy load.
- **Carry weight** and a small inventory, with bags as equipment.
- **Skills that level by doing** (light): carpentry, first aid, aiming, maintenance, sneaking.
- **Permadeath option:** when you die you turn, and you start a new survivor
  in the same world. You can then meet your old self as a walker.

## 7. World systems

- **Day counter** and an **outbreak timeline**:
  - Days 0–7: NL-Alert style emergency broadcasts on radio and TV, then silence.
  - The **power grid** fails after N days (sandbox option): lights,
    fridges and electric doors stop.
  - **Water** fails after N days: taps stop, so collect rain or boil canal water.
- **Loot never respawns.** Scavenging gets harder and riskier over time.
- **Barricades:** nail planks over windows and doors, and reinforce them with
  metal. Walkers attack them, and repairs cost materials.
- **Noise sources:** gunshots, car alarms, breaking glass, doors, sprinting,
  generators.
- **Meta events (PZ):**
  - a helicopter flying over draws herds;
  - distant gunshots;
  - an evacuation flare over the Euroborg.
- **Camouflage (TWD):** smear walker guts on yourself to walk through a herd
  until it rains or you run.

## 8. Groningen-specific content

| Place | Gameplay role |
|---|---|
| **Diepenring** (canal around the centre) | A natural moat. Hold the bridges and the inner city becomes a safe zone. |
| **Martinitoren** (97 m) | The best lookout in the city. Walkers are visible from far away on the flat land. |
| **UMCG** (hospital) | Where the outbreak started: medical loot, very dense walkers, story clues. |
| **Euroborg** (stadium) | A failed military evacuation centre, like PZ's FEMA camps or TWD's refugee center. Military loot. |
| **Zernike Campus** | Labs and research: the source of the story, rare loot. |
| **Hoofdstation** (main train station) | Rail lines out of the city, chokepoints. |
| **Eemskanaal / Van Starkenborghkanaal** (big shipping canals) | Large water barriers. Boats are a quiet way to travel. |
| **Paterswoldsemeer / Hoornseplas** (lakes) | Islands and boats for a hard-to-reach base. |
| **Bikes everywhere** | A silent but exposed way to travel. Cars are fast and loud. |
| **Groningen gas field** | Flavour, and later a fuel source for generators. |

**Map data file (later phase):** export the OSM tags for Groningen (hospital,
pharmacy, supermarket, police, school, etc.) into a small JSON shipped with
the map. The mod then knows that a building is a pharmacy and gives its
containers pharmacy loot, which makes the "local knowledge" pillar real.

## 9. People (TWD heart, later phase)

- Survivor NPCs: loners, families and raiders, who can be recruited, traded with or fought.
- Factions with territories, for example students holding Zernike, a militia at
  the Euroborg, and raiders on the ring road.
- Community building: recruits get jobs (guard, farmer, medic, scavenger).

## 10. Sandbox settings (PZ-style)

Almost every hard number above is a world option, so you can tune the game
anywhere from TWD-heroic to PZ-brutal:

- walker speed and population
- herd size
- infection chances
- the day power and water shut off
- loot rarity
- permadeath on or off
- whether vanilla mobs are on

## 11. Build phases

| Phase | Content | Playable result |
|---|---|---|
| 0 | Fabric 26.3 project setup and build ✅; Groningen map guide ✅; load the Arnis map | Mod loads ✅, map loads |
| 1 | **Strip vanilla:** remove mobs, vanilla recipes, ores, the Nether/End, XP, the vanilla HUD and advancements. **Walker basics:** slow walkers, headshots, hearing and sight, noise system, everyone turns | Groningen with walkers, and nothing vanilla left to see |
| 2 | **Body and HUD:** injuries per body part, bleeding, bandages, Knox infection, moodles, thirst and fatigue | PZ-style survival |
| 3 | **Inventory and crafting:** weight-based inventory, bags, containers, the new recipe system, loot tables, barricades | Scavenge and fortify |
| 4 | **Character and menus:** title screen, scenario picker, professions and traits, skills, sandbox settings screen | Starts like its own game |
| 5 | **World winds down:** day counter, power and water shutoff, broadcasts, meta events, herds that migrate, weapons, guts camouflage | Full loop |
| 6 | **Groningen data:** building-aware loot and spawns from OSM; modpack release with the world | Groningen feels real |
| 7 | **People:** survivor NPCs, factions, community | The TWD heart |

## 12. Open questions

**Decided:**
- **Building:** mostly vanilla. Free breaking and placing, but it makes noise and there are no vanilla recipes or ores.
- **Players:** singleplayer first, but everything is built to work on a server.
- **Default difficulty:** PZ-brutal with slow walkers. Loot is scarce, infection risk is high, and you get one life per character.
- **Story:** a sandbox with story clues. Notes, broadcasts and places like
  the UMCG and Zernike slowly reveal what happened. There's no forced ending.

**Still open:**
1. How important are guns and vehicles (cars, bikes, boats)? Vehicles are a lot of work.
2. Should we use existing mods as dependencies (GeckoLib for animations is
   almost certain), or keep everything in-house?
