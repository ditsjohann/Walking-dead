# Walking Dead (working title)

A Fabric survival mod for Minecraft 26.3 mixing The Walking Dead and Project Zomboid,
played on a real 1:1 copy of Groningen generated with
[Arnis](https://github.com/louis-e/arnis).

- [Design](docs/DESIGN.md) — concept, systems, build phases, open questions
- [Groningen map](docs/MAP_GRONINGEN.md) — how to generate the map with Arnis

Status: phase 0 done (project builds and loads). Next: phase 1, walkers.

## Development

Requires Java 25.

```bash
./gradlew build        # jar in build/libs/
./gradlew runClient    # start Minecraft with the mod
./gradlew runServer    # start a dedicated server with the mod
```
