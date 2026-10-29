# The HUD

The vanilla HUD is gone entirely. Only chat, titles, the action bar, the sleep overlay and the tab
list are kept. Everything is drawn in the dark-paper style of the [UI](UI.md).

![At rest](screenshots/hud/1-at-rest.png)
![Lined up on a walker's head](screenshots/hud/2-headshot.png)

| Element | Where | What it shows |
|---|---|---|
| **Hotbar** | bottom centre | 9 slots on a strip of paper, the selected slot circled in red marker. Your offhand gets its own scrap to the right. |
| **Item name** | above the hotbar | When you switch, the item's name and condition (e.g. `Baseball Bat 70%`) show on a paper tag for 2½ seconds. |
| **Crosshair** | centre | A small dot. It turns **red with corner brackets when a walker's head is within reach**: swing now. A thin bar under it fills up while your swing recovers. |
| **Status panel** | bottom left | A taped-on note: body doll (wounds); day and time (`Day 3 12:00`); health %; noise meter; carry weight (red when overloaded). |
| **Noise meter** | in the status panel | How much noise your movement makes: **Silent** (still), **Quiet** (sneaking), **Noisy** (walking), **Loud** (running). Walkers hear it. |
| **Moodles** | top right | Status icons on paper tags: grey, brown, then red as it gets worse; level 4 flickers (see phase 2). |
| **Breath** | above the hotbar | A thin blue bar with an ink outline, only while you're underwater. |

In creative mode only the hotbar and crosshair show. The health panel (**H**) has all the details.

![Running: Loud](screenshots/hud/4-running.png)
![Underwater: breath bar](screenshots/hud/5-underwater.png)
