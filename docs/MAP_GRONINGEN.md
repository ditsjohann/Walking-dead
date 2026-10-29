# Generating the Groningen map with Arnis

Arnis (https://github.com/louis-e/arnis, Apache-2.0) builds a Minecraft world
from OpenStreetMap data. It has to run on **your own PC**, because the dev
sandbox can't reach Arnis's map servers.

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
      --map-item false
```

- `--interior` furnishes buildings and adds loot chests.
- `--post-apocalyptic` gives the abandoned look.
- `--map-item false` skips Arnis's data pack, which may need a newer version
  than 1.21.1.
- Don't enable the gameplay extras (`--zombie-outbreak`, `--traffic-lights`, ...).
  They need 1.21.4–1.21.6+, and our mod will supply the zombies.

When the test area works, run it again with the city bounding box.

## 3. Load test in Minecraft 1.21.1

Arnis officially supports Java **1.21.4+**. Its docs say the chunks are
written in the 1.21.1 format and only the bundled `level.dat` (the world
settings file) is from 1.21.4. So swapping in a 1.21.1 `level.dat` should let
1.21.1 open the world. This is the test that decides our Minecraft version.

1. In **Minecraft 1.21.1**, create a new world called `blank`:
   - World Type: *Superflat*
   - Customize → Presets → **The Void**
   - Game mode: whichever you like
   Open it once, then quit.
2. Back up the Arnis world folder.
3. Copy `saves/blank/level.dat` over `saves/<arnis world>/level.dat`.
4. Open the Arnis world in 1.21.1 and run
   `/tp @s <x> <y> <z>` to the spawn Arnis reported (or explore with `/gamemode spectator`).

Report back:
- Does the world open, or does 1.21.1 refuse or crash? If so, send the last
  lines of `logs/latest.log`.
- Are buildings, roads and canals there?
- Are there obvious holes or missing blocks? Those are blocks that only
  exist in newer versions.

**If it fails:** we either write a small converter (rewrite each chunk's
`DataVersion` and replace newer blocks), or move the mod to Fabric 1.21.4+.

## 4. Sharing the world

The world is built from OpenStreetMap data (ODbL). If you ever share it,
credit "© OpenStreetMap contributors".
