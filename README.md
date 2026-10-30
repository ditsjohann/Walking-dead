# Walking Dead (working title)

A Fabric survival mod for Minecraft 26.3 mixing The Walking Dead and Project Zomboid,
played on a real 1:1 copy of Groningen generated with
[Arnis](https://github.com/louis-e/arnis).

- [Design](docs/DESIGN.md) — concept, systems, build phases, open questions
- [Groningen map](docs/MAP_GRONINGEN.md) — how to generate the map with Arnis
- [Screenshots](docs/SCREENSHOTS.md) — what it looks like so far
- [HUD](docs/HUD.md) — our own HUD, replacing vanilla's
- [UI](docs/UI.md) — the dark-paper look, 18-slot inventory, a screen for every container
- [Phase 1](docs/PHASE1.md) — walkers, noise, vanilla removed
- [Phase 2](docs/PHASE2.md) — wounds, infection, needs, moodle HUD
- [Phase 3](docs/PHASE3.md) — loot, barricades, weapons, carry weight, bags
- [Phase 4](docs/PHASE4.md) — vehicles: parts, fuel, keys, hotwiring, cars on the roads; detailed cars with round wheels, a real cabin, hands on the wheel and a clickable ignition
- [Phase 5](docs/PHASE5.md) — title screen, new survivor (scenarios, sandbox settings), professions, traits, skills
- [Phase 6](docs/PHASE6.md) — the world winds down: water and power going off, the city going dark, fuel pumps, taps, NL-Alerts and the radio, the helicopter, flares, herds on the move, guts camouflage
- [Combat](docs/COMBAT.md) — walkers that react to every hit, 24 walker skins, crawlers, 3D weapons, swings and punches, gore (limbs coming off, blood and puddles)

Status: phase 5 done (character and menus), plus the combat overhaul, gore and the vehicle upgrade. Phase 6 (the world winding down) in progress.

## Development

Requires Java 25.

```bash
./gradlew build        # jar in build/libs/
./gradlew runClient    # start Minecraft with the mod
./gradlew runServer    # start a dedicated server with the mod
```

Server test scenarios (uses Carpet fake players, dev only):

```bash
python3 tools/servertest/driver.py . tools/servertest/phase1.txt run/phase1.log
python3 tools/servertest/driver.py . tools/servertest/phase2.txt run/phase2.log
python3 tools/servertest/driver.py . tools/servertest/phase3.txt run/phase3.log
python3 tools/servertest/driver.py . tools/servertest/ui.txt run/ui.log
python3 tools/servertest/driver.py . tools/servertest/phase4.txt run/phase4.log
python3 tools/servertest/driver.py . tools/servertest/combat.txt run/combat.log
python3 tools/servertest/driver.py . tools/servertest/phase5.txt run/phase5.log
```

Client test with screenshots (real game window; on a headless Linux box use a virtual display):

```bash
SDL_VIDEO_FORCE_EGL=1 xvfb-run -a ./gradlew runClientGameTest   # build/run/clientGameTest/screenshots/
```

Textures are generated from ASCII pixel art (`python3 tools/textures/make_textures.py`) and, for the UI, by `python3 tools/textures/hudart.py export`; vehicle models and textures by `python3 tools/vehicles/make_vehicles.py`; the walker model and skins by `python3 tools/walkers/make_walkers.py`; blood puddles by `python3 tools/gore/make_gore.py`; the phase 6 blocks, items and sounds by `python3 tools/world/make_world.py`; the 3D weapons by `python3 tools/weapons/make_weapons.py`; the title panorama, logo, widget skins and menu pages by `python3 tools/menu/make_menu.py`; loot tables from compact lists (`python3 tools/data/make_loot_tables.py`).
