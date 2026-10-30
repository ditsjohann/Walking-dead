# Phase 5: character and menus

![The title screen](screenshots/phase5/title.png)

The game now starts like its own game: our own title screen, a "New survivor" flow like Project
Zomboid's (when, how hard, which world), and a character screen where you make your survivor from a
profession and traits. Skills level up as you use them, and the sandbox settings set how hard the
dead are.

## The title screen
- **Groningen burning at dusk** turns slowly behind every menu: the Martinitoren, the Der Aa-kerk,
  the Groninger Museum, gabled canal houses, wind turbines on the flat land, smoke, abandoned cars
  and bicycles, and walkers in the street.
- **Continue** opens the world you played last. **New survivor** starts the flow below. **Load a
  world** and **Join a server** are Minecraft's own lists. **Options** and **Quit** are as usual.
- Every Minecraft button, slider and text field is reskinned dark, with a worn tan border that turns
  gold under the mouse, like the HUD.

## New survivor
1. **When:** the scenario.
2. **How hard:** a preset, or your own rules ("Sandbox settings...").
3. **Map:** the Arnis map of Groningen (you pick where you start, by place), or any other world
   (you start at the world spawn).
4. **Pick the world:** an existing world, or a new one ("Create New World"). The rules go to the next
   world that opens and hasn't been played before. A world that's already been played keeps its own
   rules.

![New survivor](screenshots/phase5/new-survivor.png)

| Scenario | Starts on | Loot | Walkers | Burnt-out cars |
|---|---|---|---|---|
| **The first day** | day 1, 07:00 | full | as set | 40% |
| **A week later** | day 7 | ×0.75 | ×1.25 | 55% |
| **Six months later** | day 180 | ×0.45 | ×1.6 | 80% |

## Sandbox settings
![Sandbox settings](screenshots/phase5/sandbox.png)

| Setting | Values | What it does |
|---|---|---|
| Walkers | **shamblers** (0.18), fast shamblers (0.24), sprinters (0.31) | Walker speed. Changes walkers already out there too. |
| Population | none, low (×0.4), **normal**, high (×1.6), insane (×2.5) | How many walkers roam around the players (the monster cap). None: no natural spawns. |
| Herds | small (1–3), **normal (2–6)**, large (4–10), huge (8–16) | How many spawn together and follow a leader. |
| Infection | off, bites only, **normal** (scratch 7%, laceration 25%, bite 100%), harsh (15/40/100) | Chance of the Knox infection from each kind of wound. |
| Loot | very rare (×0.3), rare (×0.6), **normal**, common (×1.5), abundant (×2.2) | How full containers are when first opened. |
| Permadeath | **on**, off | On: a dead survivor is gone, and you make a new one. Off: you come back as the same person, skills and all. You still turn. |
| Vanilla monsters | **off**, on | Lets Minecraft's monsters back in, alongside the walkers. |
| Arnis bounding box | `53.17,6.48,53.26,6.65` | The box the map was generated with (see [the map guide](MAP_GRONINGEN.md)), so start points land in the right place. |

| Preset | Walkers | Population | Herds | Infection | Loot | Permadeath |
|---|---|---|---|---|---|---|
| **Survivor** (TWD heroics) | shamblers | low | small | bites only | common | off |
| **Apocalypse** (the default, PZ) | shamblers | normal | normal | normal | normal | on |
| **Builder** | shamblers | low | small | off | abundant | off |
| **Nightmare** | sprinters | high | large | harsh | rare | on |

On a server the world starts on Apocalypse; an operator changes it with `/outbreak sandbox` (the same
screen) or the commands below.

## Making your survivor
When you join a world without a survivor (first time, or after dying with permadeath), the
character screen comes up. Until you're done, nothing can hurt you and walkers ignore you.

![Making a survivor](screenshots/phase5/character.png)

- **Name:** type one, or **?** for a random Dutch one.
- **Profession:** what you did before. It gives skill levels, sometimes a trait, your starting kit,
  and the points you have for traits.
- **Traits:** good ones cost points, bad ones give points back. You can't start with fewer than 0
  points. Opposites (Strong and Weak...) can't go together. Hover over one for what it does.
- **At:** where you wake up. On the Groningen map: the Grote Markt, the Hoofdstation, the UMCG,
  Zernike Campus, the Euroborg, the Paterswoldsemeer, or anywhere (one of those at random). The spot
  is worked out from its latitude and longitude the way Arnis lays out the map. Then the lowest dry
  ground nearby is picked, so not a roof or a canal.
- Everyone starts with a bottle of clean water, a sandwich and two rags, plus the profession's kit.

| Profession | Points | Skills | Comes with | Kit |
|---|---|---|---|---|
| Unemployed | +8 | — | — | 2 bread |
| Nurse | +2 | First aid 3, Fitness 1 | — | 3 bandages, disinfectant |
| Police officer | −4 | Fighting 3, Fitness 1, Strength 1 | — | police baton (new weapon) |
| Carpenter | +2 | Carpentry 3, Maintenance 1 | — | hammer, 16 nails, 8 planks, worn axe |
| Mechanic | +2 | Mechanics 3, Maintenance 1 | — | wrench, screwdriver |
| Student | +4 | Fitness 1 | Fast learner | school bag, bicycle |
| Farmer | +4 | Strength 1, Carpentry 1 | — | worn axe, hoe, seeds, potatoes |
| Firefighter | 0 | Fitness 2, Strength 2 | — | axe |
| Construction worker | +2 | Strength 2, Carpentry 1 | — | worn pickaxe, crowbar |
| Chef | +4 | Fighting 1, Maintenance 1 | — | kitchen knife, bread, steak |
| Burglar | −2 | Sneaking 2, Mechanics 1 | Burglar | crowbar, screwdriver |

| Good trait | Cost | | Bad trait | Gives |
|---|---|---|---|---|
| Strong: +4 kg, hits shove harder | 6 | | Weak: −4 kg, hits barely shove | 6 |
| Athletic: a little faster, tires much more slowly | 6 | | Out of shape: a little slower, tires much faster | 4 |
| Fast learner: skills +30% | 4 | | Slow learner: skills −30% | 4 |
| Lucky: ×1.25 loot, +5% hotwiring | 4 | | Unlucky: ×0.75 loot, −5% hotwiring | 4 |
| Brave: panic builds half as fast | 4 | | Cowardly: panic builds 1.6× as fast | 3 |
| Thick skinned: a quarter of walker hits don't break the skin | 6 | | Thin skinned: a quarter of walker hits go a step deeper | 5 |
| Fast healer: wounds and health ×1.4 | 4 | | Slow healer: ×0.7 | 4 |
| Light-footed: footsteps ×0.6 as far | 3 | | Clumsy: footsteps ×1.5 as far | 3 |
| Camel: thirst ×0.7 | 3 | | Thirsty: thirst ×1.4 | 3 |
| Inconspicuous: walkers see you ×0.75 as far | 4 | | Conspicuous: ×1.3 as far | 4 |
| Organized: a worn bag carries 15% lighter | 3 | | Hemophobic: bleeding makes you panic | 3 |
| | | | Prone to illness: wounds and bandages infect twice as easily | 3 |
| | | | Short-sighted: fog beyond about 30 blocks | 2 |

**Burglar** (only from the profession): hotwires with bare hands, and +25% to the chance.

## Skills
Eight skills, from 0 to 10, levelling by doing. Level 1 takes 75 xp, level 5 1125, level 10 4125.
A message and a little sound tell you when you level up.

| Skill | Practised by | Each level |
|---|---|---|
| Fighting | hitting walkers (+2, +3 on the head; +5 for a kill) | +5% melee damage |
| Maintenance | every hit with a weapon or tool (+1) | 6% chance a hit doesn't wear it |
| Carpentry | nailing a plank (+6), prying one off (+2) | 7% chance a plank goes up without using the plank and nails |
| First aid | treating a wound (+8) | bandaged wounds heal 6% faster; bandages infect 8% less |
| Mechanics | hotwiring (+5 a try), changing parts (+8, +3 taking off), repairs (+12 engine, +8 windows) | +5% hotwiring, +2% engine and +5% windows per repair |
| Sneaking | creeping (crouched, moving) with walkers within 16 blocks (+1.5/s) | footsteps 6% quieter; walkers see you crouching 4% less far |
| Fitness | sprinting (+1/s) | tire 4% slower |
| Strength | moving while carrying over 75% of your capacity (+1/s), hitting walkers (+0.5) | +0.5 kg capacity, 3% more push |

No xp in creative or spectator mode.

## The character sheet (K)
Your name, profession, the day, how many days you've survived and walkers you've put down, your
traits, every skill (filled boxes for levels, red for the one you're working on), and how much you
can carry.

![The character sheet](screenshots/phase5/sheet.png)

## Death
With permadeath on, your survivor is gone: you still turn (your old self rises with your survivor's
name), and when you respawn you make a new one. The character screen remembers them: "In memory of
Jan Pruim: 3 days, 12 walkers put down." With permadeath off you keep your survivor, but not your
things.

## Admin and testing commands (operators only)
```
/outbreak sandbox                         — the settings screen
/outbreak sandbox info
/outbreak sandbox preset <survivor|apocalypse|builder|nightmare|custom>
/outbreak sandbox scenario <first_day|week_later|six_months>   (doesn't move the clock)
/outbreak sandbox set <setting> <value>   — walker_speed, population, herd_size, infection, loot, permadeath, vanilla_monsters
/outbreak character info <player>
/outbreak character create <player> <profession> [trait,trait,...]   — with the kit, where they stand
/outbreak character reset <player>        — gone: they get the character screen
/outbreak character xp <player> <skill> <amount>
```

## How it's made
- **Menu art:** `python3 tools/menu/make_menu.py` paints the 360° panorama as one strip and projects it
  onto the six faces of the cube Minecraft draws behind its menus. It also makes the logo, the dark
  widget skins (they replace Minecraft's own, so every screen gets them) and the paper pages of the
  new screens.
- **The title screen** is a TitleScreen subclass swapped in wherever Minecraft opens its own, so
  everything that waits for the title screen still finds one.
- **Rules** are saved with the world (as `outbreak:sandbox` saved data) and sent to every player. **Survivors**
  are kept on the player and synced to them.
- Test players (Carpet's fake players and the client game tests) can't fill in a form, so they get a
  plain survivor (unemployed, with the old test kit) straight away.

## Tests
- Server: `python3 tools/servertest/driver.py . tools/servertest/phase5.txt run/phase5.log`
  (default rules; professions, traits and their kits; xp with slow and fast learners; carpentry from a
  plank; sprinters and back; abundant against very rare loot).
- Client: `CharacterClientTest` (the title screen, new survivor, sandbox settings, making a nurse,
  arriving, the character sheet, the operator's editor in game).

## Not yet
Choosing your looks and clothes, more professions (doctor, soldier...), skill books, a spawn map
to click on, multiple saved survivors per world, and the power and water shut-off days (phase 6).
