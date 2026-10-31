# Phase 7: Groningen's own data

Arnis builds the city's buildings, but not what they are: to the game a pharmacy was just a house
with shelves. Now every shop, pharmacy, hospital, school, police station, petrol station, office,
church, gym, hotel, factory and barracks in Groningen is on the map, from OpenStreetMap
(3,152 places so far), and the game knows which building you're in.

## Loot by building
A cupboard, chest or barrel in a known building is filled from that kind of building's own list
(first look only, as before; the sandbox loot setting and the Scavenger perks still apply). In a
house, or anywhere off the map, it still goes by the room (a kitchen, a bedroom...). Our own
containers (fridges, toolboxes, medicine cabinets...) keep their own lists.

| Building | What you find |
|---|---|
| Hospital, clinic, pharmacy | Bandages, disinfectant, rags, clean water |
| Police station | 9 mm rounds, batons, a pistol now and then, rarely a shotgun, radios |
| Barracks (military land) | Rifle and pistol rounds, a worn hunting rifle or pistol, bandages, hiking bags, fuel |
| Fire station | Crowbars, axes, bandages, hoses, jerrycans |
| Petrol station | Jerrycans (some with fuel), a car battery, snacks, water, a hose, a tire |
| Supermarket, grocery | Food, drinks, and some of it rotten |
| Hardware store | Hammers, lots of nails, crowbars, wrenches, screwdrivers, planks, torches |
| Bike shop | Bike tires, tools, now and then a bicycle |
| Garage, car dealer | Tires, car batteries, engine parts, jacks, wrenches |
| Clothes shop | Cloth, leather, rags, bags, boots |
| Electronics | Batteries, radios |
| Bookshop, library | Books, paper, maps, candles |
| Restaurant, café | Cooked food, water, kitchen knives |
| Bar | Drinks, empty bottles, snacks, now and then a baseball bat |
| School | Paper, books, school bags, apples and cookies |
| Church | Candles, books, bread, rags |
| Hotel, gym, office, factory, other shops | Each its own list |

## Walkers by building
- **Dressed for where they were:** most walkers that turn up in a hospital wear scrubs or a doctor's
  coat, at the police station a uniform, in a school they're students, in a restaurant chefs, at
  the barracks soldiers, in a hardware store or a factory builders, in a gym joggers and football
  shirts, in an office office clothes, at a church the elderly.
- **What they had on them:** a dead police officer may have 9 mm rounds or a baton (and, rarely, a
  pistol), a soldier rifle rounds and a bandage, a nurse or doctor bandages and disinfectant, a
  builder nails or a hammer, a chef a kitchen knife, a student a school bag.
- **Crowded places:** hospitals, police stations, schools, supermarkets and the barracks are full of
  them, wherever they are in the city.

## Where they turn up
Walkers spawn naturally (see [phase 1](PHASE1.md)), but now:
- **Never where you can see it**, and never closer than 32 blocks.
- **A herd at a time:** the area around each survivor fills up over a couple of minutes, instead of
  vanilla's whole monster cap at once.
- **Not on rooftops** (inside buildings, on any floor, is fine).
- **Thick in the centre:** around the Grote Markt, the UMCG, the Euroborg and the main station
  everything spawns; it thins out from the centre to a quarter of that in the suburbs. Crowded
  places (above) are full anywhere. Other maps: evenly, a bit thinner than vanilla.

## Commands (operators)
```
/outbreak place                  - what building you're standing in (and how many the map knows)
/outbreak walker census <radius> - how many walkers are around, and how far
```

## How it's made
- `python3 tools/data/make_places.py` asks an Overpass API server (OpenStreetMap) for everything in
  Arnis's box of the city, tile by tile, and works out a kind for each from its tags (the first rule
  that matches: `amenity=pharmacy` is a pharmacy, `shop=bicycle` a bike shop, `landuse=military` the
  barracks...). Shops mapped as a point get the box of the building they stand in. It writes
  `data/outbreak/places/groningen.json`: the kind and a latitude/longitude box for each place. The
  raw answers are cached in `tools/data/cache/` (not committed). Ways sent with only a centre get
  their box from a quick lookup by id. The public servers are often overloaded: a tile they keep
  giving up on is split in four, then left out for that run (a rerun fetches only what's missing),
  and `OFFLINE=1` builds the file from the cache alone.
- **What's in so far:** every shop and amenity (3,152 places: 683 shops, 613 restaurants and cafés,
  342 offices, 252 clothes shops, 207 schools, 181 clinics, 63 supermarkets, 32 pharmacies, 39
  petrol stations, 6 police stations, the UMCG, the Martini Ziekenhuis and Beatrixoord...). Still to
  fetch: the building-type outlines (offices, warehouses mapped only as buildings), military and
  industrial land, and the buildings round shops mapped as a point (those get a 24 m square
  for now).
- In game, `Places` projects the boxes onto the map with the world's own Arnis box (the same
  projection as the start points), indexes them in a 64-block grid, and answers "what's here" with
  the smallest box that contains the spot. Only on the Groningen map.
- The loot lists are `containers/place_<kind>` tables, written from compact lists by
  `python3 tools/data/make_loot_tables.py`.
- `WalkerSpawning` checks each natural spawn: distance, sight lines (two rays from every survivor's
  eyes within 96 blocks), the survivor's budget (one herd to start with, then one more every 8 seconds, at most 4 waiting), roofs, and the
  density.

## Tests
- Server: `phase7.txt` (standing in a pharmacy, a police station and the UMCG, `/outbreak place`
  names it and a chest there fills from its list: bandages and disinfectant, 9 mm rounds and a
  radio, rags and bottles; out in a field a chest goes by the room), `spawning.txt` (a survivor in a walled field: the count after 30 s, 1, 2 and 3 minutes;
  none closer than 32 blocks, none in sight), `persist_a.txt` then `KEEP_WORLD=1 persist_b.txt`
  (a car with its key in, a walker's skin and lost limbs, the power and water being off, guts on
  you and a gun's rounds all survive saving and loading).
- Client: everything else is unchanged; the screenshots of earlier phases still apply.

## Not yet
Building-specific interiors (Arnis's are generic), the modpack release with the world.
