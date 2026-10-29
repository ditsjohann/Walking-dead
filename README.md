# Walking Dead (working title)

A Fabric survival mod for Minecraft 26.3 mixing The Walking Dead and Project Zomboid,
played on a real 1:1 copy of Groningen generated with
[Arnis](https://github.com/louis-e/arnis).

- [Design](docs/DESIGN.md) — concept, systems, build phases, open questions
- [Groningen map](docs/MAP_GRONINGEN.md) — how to generate the map with Arnis
- [Phase 1](docs/PHASE1.md) — what's in the game now

Status: phase 1 done (walkers, noise, vanilla removed). Next: phase 2, body and HUD.

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
```
