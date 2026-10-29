# Generating the Groningen map with Arnis

Arnis (https://github.com/louis-e/arnis, Apache-2.0) builds a Minecraft world
from OpenStreetMap data. It has to run on **your own PC**, because the dev
sandbox can't reach Arnis's map servers. The mod targets **Minecraft 26.3**,
which Arnis supports directly.

## 1. Areas

| Name | Bounding box (`min_lat,min_lng,max_lat,max_lng`) | Size at 1:1 | Use |
|---|---|---|---|
| **Test** (inner city inside the Diepenring) | `53.210,6.550,53.226,6.585` | ≈ 2 × 2.3 km (~4 km²) | Quick test that everything works |
| **Groningen city** (city, ring road, Zernike, the lakes to the south) | `53.17,6.48,53.26,6.65` | ≈ 10 × 11 km (~113 km²) | The first real map |

Spawn point: Grote Markt, next to the Martinitoren → `53.2193, 6.5681`.

For scale: 113 km² is in Arnis's "yellow" range (about 4 GB of RAM). The
province of Groningen (~2,960 km²) is far too big for one run. Later
regions can be added one at a time with Arnis's One World mode.

## 2. Generate

Download the latest Arnis release from its GitHub releases page and run the
GUI, or use the command line:

```bash
arnis --bbox="53.210,6.550,53.226,6.585" \
      --output-dir="<path to .minecraft>/saves" \
      --spawn-lat=53.2193 --spawn-lng=6.5681 \
      --interior --post-apocalyptic \
      --gamemode survival
```

- `--interior` furnishes buildings and adds loot chests.
- `--post-apocalyptic` gives the abandoned look.
- **Don't** use `--zombie-outbreak`. Our mod supplies the walkers. Other
  gameplay extras (for example `--traffic-lights`) are fine to try.
- Arnis creates a void world by default, so outside the map edge there's
  nothing. That suits us.

When the test area works, run it again with the city bounding box.

## 3. Play it

1. Install **Minecraft 26.3** with Fabric Loader (0.20.1+) and Fabric API
   for 26.3, then put the `outbreak` jar in `mods/`.
2. Open the generated world.

Things to check on the first test:
- Buildings, roads, canals (the Diepenring) and interiors are there.
- Performance is OK in the city centre. If not, add Sodium; Distant
  Horizons gives far views from the Martinitoren.

## 4. Sharing the world

The world is built from OpenStreetMap data (ODbL). If you ever share it,
credit "© OpenStreetMap contributors".
