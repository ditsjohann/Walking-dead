# Phase 1: what's in the game now

## Walkers
- **Slow shamblers** (speed 0.18; a vanilla zombie is 0.23). They speed up a little when they lunge at you.
- **They don't burn** in daylight and **don't turn into drowned**: they walk along canal bottoms.
- **Only the head counts:**
  - A hit in the top ~22% of the body does ×4 damage, so a worn iron axe kills in one swing.
  - Body hits do half damage, stagger the walker and **can never kill it**: it stays at 1 HP.
  - Hits knock walkers about, floor them, and can leave them crawling: see [Combat](COMBAT.md).
  - Explosions, fire, falling and `/kill` still kill normally.
- **Hearing:**
  - Walkers walk to the last noise they heard, look around there, then forget it after 30 seconds.
  - Louder or newer noises win.
  - While a walker can see you, it ignores noises.
- **Sight:**
  - Range is 6 blocks plus the light level where you stand (6 in the dark, 21 in full daylight).
  - Crouching halves it.
  - They only see in a ~130° cone in front of them, except within 3 blocks.
- **Herds:** walkers spawn in groups of 2–6. The first one in a group becomes the leader and the others
  follow it around. A hit on one walker alerts the walkers around it.
- **Doors:** they bash doors down on every difficulty.
- **Spawning:** walkers are the only monster that spawns naturally, by day and night, indoors and
  outdoors, in any world. The density is vanilla's monster cap for now.
- **Model:** the vanilla zombie model is a stand-in until they get their own.

## Noise (radius in blocks)

| Source | Radius |
|---|---|
| Footsteps (crouching is silent, sprinting is ×2.5) | 4 |
| Landing, swimming, splashing | 6 / 5 / 8 |
| Placing / breaking a block | 8 / 12 |
| Doors and trapdoors | 8 |
| Opening chests | 5 |
| Getting hurt (you cry out) | 10 |
| Shooting / a projectile landing (you can throw things to lure walkers away) | 10 / 8 |
| Note block / jukebox / goat horn | 16 / 32 / 48 |
| Lightning / explosions | 48 / 64 |

Crouching halves every noise you make. Noises from creative and spectator players are ignored.

## Everyone is infected
When a player dies, of any cause, a walker with their name gets up in the same spot 30 seconds later.
That walker never despawns, so you can find your old self. Your items drop as normal.

## Vanilla removed
- **Monsters:** all of them (creepers, skeletons, endermen, vanilla zombies...). Villagers, wandering
  traders, golems and allays are gone too. They can't even be summoned with commands.
- **No XP:** XP orbs no longer exist, and the XP bar and level are hidden.
- **No advancements**, and no advancement toasts.
- **Nether portals** don't light.
- **Recipes:** 1,468 of them stay: building blocks, furniture, food and boats. Removed: tools, weapons,
  armour, redstone devices, rails/minecarts, enchanting, anvils, brewing, beacons, ender items,
  compass/clock/map, and smelting ores.
- **No punching:**
  - Wood (logs, planks, wooden stairs, slabs, fences, doors, trapdoors) needs an axe.
  - Stone, brick, concrete, metal and anything else that needs a tool for drops needs a pickaxe.
  - Without the right tool these blocks don't break at all.
  - Dirt, sand, leaves, glass and plants still break by hand.

## Temporary
- **Starter kit:** every new survivor starts with a worn iron axe, a worn stone pickaxe, 3 bread and 6
  torches. This holds until scavenging (phase 3) and professions (phase 5) replace it.
- **HUD:** hearts and hunger are still the vanilla ones until phase 2.

## Try it
1. Build with `./gradlew build` and put `build/libs/outbreak-0.1.0.jar` in your Minecraft 26.3 Fabric `mods/`.
2. Open your Arnis Groningen world.
3. Or run `./gradlew runClient` to start a dev client.

Automated server checks: `python3 tools/servertest/driver.py . tools/servertest/phase1.txt run/phase1.log`.
