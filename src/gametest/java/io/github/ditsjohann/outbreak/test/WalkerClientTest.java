package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Screenshots of walkers: every skin, a herd on the move, hit reactions frame by frame, falling over
 * in each direction, getting back up, a crawler, and a headshot kill falling back.
 * <p>
 * The player stands at the origin facing south; the walkers face back north at them. Yaws are the
 * direction a hit pushes: 0 = away from the player, -90 = east (the player's left), 90 = west.
 */
public class WalkerClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 1500");
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");

			// 1. Every skin, eight at a time.
			for (int i = 0; i < 8; i++) {
				server.runCommand("execute as @p at @s run summon outbreak:walker ~" + (i * 1.3 - 4.5) + " ~ ~5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"lineup\",\"l" + i + "\"]}");
			}

			for (int page = 0; page < 3; page++) {
				for (int i = 0; i < 8; i++) {
					server.runCommand("outbreak walker skin @e[tag=l" + i + "] " + (page * 8 + i));
					server.runCommand("outbreak walker crawler @e[tag=l" + i + "] false");
				}

				context.waitTicks(10);
				context.takeScreenshot("walkers-1-skins-" + (page + 1));
			}

			this.clear(context, server, "lineup");

			// 2. A herd shambling towards the player (survival, so they come).
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @p minecraft:resistance 600 4 true");
			for (int i = 0; i < 6; i++) {
				server.runCommand("execute as @p at @s run summon outbreak:walker ~" + (i * 1.5 - 4) + " ~ ~" + (14 + i % 2 * 2) + " {PersistenceRequired:1b,Tags:[\"herd\"]}");
			}

			context.waitTicks(70);
			context.takeScreenshot("walkers-2-herd");
			this.clear(context, server, "herd");
			server.runCommand("gamemode creative @a");
			context.waitTicks(5);

			// 3. A bat to the side of the head (pushing to the player's left): frame by frame.
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~3 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"hit\"]}");
			context.waitTicks(10);
			server.runCommand("outbreak walker hit @e[tag=hit] -90 1.3 true");
			for (int frame = 0; frame < 4; frame++) {
				context.waitTicks(2);
				context.takeScreenshot("walkers-3-head-hit-" + frame);
			}

			// ...a shove in the chest, from the front.
			context.waitTicks(30);
			server.runCommand("outbreak walker hit @e[tag=hit] 0 1.0 false");
			context.waitTicks(3);
			context.takeScreenshot("walkers-4-shove");
			this.clear(context, server, "hit");

			// 4. Knocked down, each one outwards: to the player's left (+x), backwards, to their right (-x).
			String[][] falls = {{"2", "-90"}, {"0", "0"}, {"-2", "90"}};
			for (String[] fall : falls) {
				server.runCommand("execute as @p at @s run summon outbreak:walker ~" + fall[0] + " ~ ~4 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"fall\"]}");
			}

			context.waitTicks(10);
			for (int i = 0; i < falls.length; i++) {
				server.runCommand("execute as @p at @s positioned ~" + falls[i][0] + " ~ ~4 run outbreak walker knockdown @e[tag=fall,limit=1,sort=nearest] " + falls[i][1]);
			}

			context.waitTicks(6);
			context.takeScreenshot("walkers-5-falling");
			context.waitTicks(20);
			context.takeScreenshot("walkers-6-down");
			// Down for 80 ticks, then 24 to get up.
			context.waitTicks(64);
			context.takeScreenshot("walkers-7-getting-up");
			context.waitTicks(30);
			this.clear(context, server, "fall");

			// 5. A crawler coming for the player.
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~1 ~ ~7 {PersistenceRequired:1b,Tags:[\"crawl\"]}");
			server.runCommand("outbreak walker crawler @e[tag=crawl] true");
			context.waitTicks(60);
			context.takeScreenshot("walkers-8-crawler");
			this.clear(context, server, "crawl");
			server.runCommand("gamemode creative @a");

			// 6. A headshot that kills: it falls back, away from the player, and lies there.
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~2.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"dead\"]}");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -5");
			context.waitTicks(10);
			server.runCommand("execute as @p run damage @e[tag=dead,limit=1] 30 minecraft:player_attack by @s");
			context.waitTicks(5);
			context.takeScreenshot("walkers-9-killed");
			context.waitTicks(25);
			context.takeScreenshot("walkers-10-corpse");
		}
	}

	/** Kills them, then waits for the corpses (which lie there 5 seconds) to go, so they don't clutter the next shot. */
	private void clear(ClientGameTestContext context, TestServerContext server, String tag) {
		server.runCommand("kill @e[tag=" + tag + "]");
		context.waitTicks(110);
	}
}
