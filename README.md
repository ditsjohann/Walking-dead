# Walking Dead (working title)

A Fabric survival mod for Minecraft 26.3 mixing The Walking Dead and Project Zomboid,
played on a real 1:1 copy of Groningen generated with
[Arnis](https://github.com/louis-e/arnis).

- [Design](docs/DESIGN.md) — concept, systems, build phases, open questions
- [Groningen map](docs/MAP_GRONINGEN.md) — how to generate the map with Arnis
- [Screenshots](docs/SCREENSHOTS.md) — what it looks like so far
- [HUD](docs/HUD.md) — our own HUD, replacing vanilla's
- [Phase 1](docs/PHASE1.md) — walkers, noise, vanilla removed
- [Phase 2](docs/PHASE2.md) — wounds, infection, needs, moodle HUD
- [Phase 3](docs/PHASE3.md) — loot, barricades, weapons, carry weight, bags

Status: phase 3 done (loot, barricades, weapons, bags). Next: phase 4, vehicles.

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
```

Client test with screenshots (real game window; on a headless Linux box use a virtual display):

```bash
SDL_VIDEO_FORCE_EGL=1 xvfb-run -a ./gradlew runClientGameTest   # build/run/clientGameTest/screenshots/
```

Textures are generated from ASCII pixel art (`python3 tools/textures/make_textures.py`), loot tables from compact lists (`python3 tools/data/make_loot_tables.py`).
