# Design — Walking Dead × Project Zomboid in Groningen

Status: **draft v0.1, still open for discussion.** Nothing is built yet.

## 1. Pitch

The outbreak started in Groningen. You wake up a few days in, in a real 1:1
copy of the city generated from OpenStreetMap. The *feel* is Project Zomboid:
slow, systemic, punishing survival where every scratch matters and you will
eventually die. The *heart* is The Walking Dead: walkers are slow but endless,
everyone is already infected, and the real story is about the community you
build and the people you meet.

> "This is how you died" — but first, how long did you last?

## 2. Platform decision

| Option | Verdict |
|---|---|
| **Fabric 1.21.1** | **Chosen.** It's your preferred version, Fabric 1.21.1 has a mature ecosystem (GeckoLib, Sodium, Distant Horizons), and nothing we need is missing. |
| NeoForge 1.21.1 | Same Minecraft version. Its edge is mostly modpack ecosystem. It doesn't make our own code better, so there's no reason to switch. |
| Forge / older versions | No benefit, and older versions can't open Arnis worlds at all. |
| Fabric 1.21.4+ | **Fallback.** Use it only if the Groningen world can't be made to load in 1.21.1 (see `MAP_GRONINGEN.md`). Arnis officially targets 1.21.4+. |

The mod does **not** depend on the map. It works in any world, and the
Groningen world is a separate download. Later, an optional "map data" file
(see §7) can give the mod extra knowledge about Groningen.

## 3. Design pillars

1. **Every wound matters** (PZ). Health is a body, not a heart bar.
2. **Noise is death** (PZ + TWD). Everything you do makes sound, and walkers come to it.
3. **The dead are slow, the herd is not** (TWD). One walker is easy. Fifty are a death sentence.
4. **Everyone is infected** (TWD). Die for any reason and you turn.
5. **The world winds down** (PZ). Power, water and broadcasts stop. Loot never comes back.
6. **Real place, real knowledge** (ours). Locals know where the hospital, the
   bridges and the supermarkets are, and that knowledge is an advantage.

## 4. Walkers

- **Movement:** slow shamblers by default. A sandbox option (see §9) allows
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

## 5. Survivor (the PZ layer)

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

## 6. World systems

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

## 7. Groningen-specific content

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

## 8. People (TWD heart, later phase)

- Survivor NPCs: loners, families and raiders, who can be recruited, traded with or fought.
- Factions with territories, for example students holding Zernike, a militia at
  the Euroborg, and raiders on the ring road.
- Community building: recruits get jobs (guard, farmer, medic, scavenger).

## 9. Sandbox settings (PZ-style)

Almost every hard number above is a world option, so you can tune the game
anywhere from TWD-heroic to PZ-brutal:

- walker speed and population
- herd size
- infection chances
- the day power and water shut off
- loot rarity
- permadeath on or off
- whether vanilla mobs are on

## 10. Build phases

| Phase | Content | Playable result |
|---|---|---|
| 0 | Fabric 1.21.1 project setup and build; Groningen map guide and 1.21.1 load test | Mod loads, map loads |
| 1 | Walker entity: slow movement, headshots, hearing and sight, noise system, herds, everyone turns, vanilla mobs off | Survive walkers in Groningen |
| 2 | Body injuries, bleeding, bandages, Knox infection, moodle HUD, thirst and fatigue | PZ-style survival |
| 3 | Day counter, power and water shutoff, broadcasts, barricades, loot tables, carry weight | The world winds down |
| 4 | Weapons (melee durability, crossbow, scarce loud guns), guts camouflage, skills, sandbox settings screen | Full loop |
| 5 | Map data file for Groningen (building-aware loot and spawns), meta events | Groningen feels real |
| 6 | Survivor NPCs, factions, community | The TWD heart |

## 11. Open questions

1. Is the game singleplayer, or a multiplayer server with friends?
2. Default difficulty: TWD (heroic, more ammo) or PZ (brutal)?
3. Should there be a story or ending (find out what happened at Zernike), or pure sandbox?
4. How important are guns and vehicles (cars, bikes, boats)? Vehicles are a lot of work.
5. Should we use existing mods as dependencies (GeckoLib for animations is
   almost certain), or keep everything in-house?
