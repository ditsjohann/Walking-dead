package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;

/**
 * Frame-by-frame screenshots of the animations, to check they line up: every weapon's swing in
 * first person and third person, punches, walking with a weapon, and walkers walking, attacking,
 * falling, lying, getting up and crawling, seen from the side.
 */
public class AnimationClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 1500");
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");

			// 1. First person: each swing, every tick.
			String[][] swings = {{"baseball_bat", "12"}, {"hammer", "10"}, {"kitchen_knife", "7"}, {"air", "8"}};
			for (String[] swing : swings) {
				String item = swing[0].equals("air") ? "minecraft:air" : "outbreak:" + swing[0];
				server.runCommand("item replace entity @p weapon.mainhand with " + item);
				context.waitTicks(25);
				context.takeScreenshot("anim-fp-" + swing[0] + "-rest");
				context.getInput().pressKey(options -> options.keyAttack);
				for (int tick = 1; tick <= Integer.parseInt(swing[1]); tick += 2) {
					context.takeScreenshot("anim-fp-" + swing[0] + "-" + tick);
					context.waitTicks(2);
				}
			}

			// 2. Walking with the bat (view bobbing).
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:baseball_bat");
			context.getInput().holdKeyFor(options -> options.keyUp, 14);
			context.takeScreenshot("anim-fp-walk-bat");

			// 3. Third person, front and back, each style mid-swing.
			for (CameraType camera : new CameraType[] {CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
				String view = camera == CameraType.THIRD_PERSON_FRONT ? "front" : "back";
				context.runOnClient(minecraft -> minecraft.options.setCameraType(camera));
				for (String[] swing : swings) {
					String item = swing[0].equals("air") ? "minecraft:air" : "outbreak:" + swing[0];
					server.runCommand("item replace entity @p weapon.mainhand with " + item);
					context.waitTicks(25);
					context.takeScreenshot("anim-tp-" + view + "-" + swing[0] + "-rest");
					context.getInput().pressKey(options -> options.keyAttack);
					for (int tick = 1; tick <= Integer.parseInt(swing[1]) / 2 + 1; tick += 2) {
						context.takeScreenshot("anim-tp-" + view + "-" + swing[0] + "-" + tick);
						context.waitTicks(2);
					}
				}
			}

			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");

			// 4. A walker shambling past, side-on and close (moved a little each tick, which drives
			// the walk animation like real walking), then one biting.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~-2.5 ~ ~3.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[-90f,0f],Tags:[\"walk\"]}");
			server.runCommand("outbreak walker crawler @e[tag=walk] false");
			context.waitTicks(5);
			for (int step = 0; step < 36; step++) {
				server.runCommand("execute as @e[tag=walk] at @s run tp @s ~0.12 ~ ~");
				context.waitTicks(1);
				if (step % 6 == 5) {
					context.takeScreenshot("anim-walker-walk-" + step / 6);
				}
			}

			server.runCommand("kill @e[tag=walk]");
			context.waitTicks(110);
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @p minecraft:resistance 600 4 true");
			server.runCommand("effect give @p minecraft:regeneration 600 4 true");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~1 ~ ~2.2 {PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"bite\"]}");
			server.runCommand("outbreak walker crawler @e[tag=bite] false");
			server.runCommand("attribute @e[tag=bite,limit=1] minecraft:movement_speed base set 0");
			for (int frame = 0; frame < 6; frame++) {
				context.waitTicks(4);
				context.takeScreenshot("anim-walker-attack-" + frame);
			}

			server.runCommand("kill @e[tag=bite]");
			server.runCommand("gamemode creative @a");
			context.waitTicks(110);

			// 5. Side-on poses, close up: standing, falling, down, getting up, crawling.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 15");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~3 {NoAI:1b,PersistenceRequired:1b,Rotation:[90f,0f],Tags:[\"pose\"]}");
			server.runCommand("outbreak walker crawler @e[tag=pose] false");
			context.waitTicks(10);
			context.takeScreenshot("anim-walker-side-standing");
			server.runCommand("outbreak walker hit @e[tag=pose] 0 1.2 true");
			context.waitTicks(2);
			context.takeScreenshot("anim-walker-side-hit");
			context.waitTicks(20);
			server.runCommand("outbreak walker knockdown @e[tag=pose] 90");
			context.waitTicks(4);
			context.takeScreenshot("anim-walker-side-falling");
			context.waitTicks(20);
			context.takeScreenshot("anim-walker-side-down");
			context.waitTicks(62);
			context.takeScreenshot("anim-walker-side-getting-up-a");
			context.waitTicks(10);
			context.takeScreenshot("anim-walker-side-getting-up-b");
			context.waitTicks(30);
			server.runCommand("outbreak walker crawler @e[tag=pose] true");
			context.waitTicks(10);
			context.takeScreenshot("anim-walker-side-crawler");
			server.runCommand("kill @e[tag=pose]");
		}
	}
}
