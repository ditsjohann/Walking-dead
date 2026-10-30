package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Screenshots of the gore: walkers missing an arm, a forearm, a leg, their head; the pieces lying
 * on the ground; a headless corpse in its pool; puddles drying; a crawler's trail; a machete hit.
 */
public class GoreClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 1500");
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 18");

			// 0. The blood particles on their own.
			server.runCommand("execute as @p at @s run particle minecraft:block{block_state:\"outbreak:blood\"} ~ ~1.6 ~3 0.3 0.3 0.3 0.5 80");
			context.waitTicks(3);
			context.takeScreenshot("gore-0-particles");
			context.waitTicks(30);

			// 1. Five walkers, each missing something; the pieces fly off and land.
			String[][] cuts = {{"-3", "right_forearm"}, {"-1.5", "left_arm"}, {"0", "right_leg"}, {"1.5", "head"}, {"3", "left_shin"}};
			for (int i = 0; i < cuts.length; i++) {
				server.runCommand("execute as @p at @s run summon outbreak:walker ~" + cuts[i][0] + " ~ ~4.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"cut\",\"c" + i + "\"]}");
				server.runCommand("outbreak walker crawler @e[tag=c" + i + "] false");
			}

			context.waitTicks(10);
			for (int i = 0; i < cuts.length; i++) {
				server.runCommand("outbreak walker sever @e[tag=c" + i + "] " + cuts[i][1]);
			}

			context.waitTicks(3);
			context.takeScreenshot("gore-1-flying");
			context.waitTicks(40);
			context.takeScreenshot("gore-2-lineup");

			// 2. The pieces on the ground, close up.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~2 0 55");
			context.waitTicks(10);
			context.takeScreenshot("gore-3-pieces");
			server.runCommand("kill @e[tag=cut]");
			server.runCommand("kill @e[type=outbreak:body_part]");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~-2 0 18");
			context.waitTicks(110);

			// 3. Decapitated, then put down: it falls into its own pool.
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~3.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"dead\"]}");
			server.runCommand("outbreak walker crawler @e[tag=dead] false");
			context.waitTicks(10);
			server.runCommand("outbreak walker sever @e[tag=dead] head");
			context.waitTicks(4);
			server.runCommand("kill @e[tag=dead]");
			context.waitTicks(30);
			context.takeScreenshot("gore-4-corpse");
			context.waitTicks(90);

			// 4. Puddles from fresh to dried.
			server.runCommand("kill @e[type=outbreak:body_part]");
			for (int age = 0; age < 4; age++) {
				server.runCommand("execute as @p at @s run setblock ~" + (age * 2 - 3) + " ~ ~6 outbreak:blood[size=2,age=" + age + "]");
				server.runCommand("execute as @p at @s run setblock ~" + (age * 2 - 3) + " ~ ~4 outbreak:blood[size=1,age=" + age + "]");
				server.runCommand("execute as @p at @s run setblock ~" + (age * 2 - 3) + " ~ ~2 outbreak:blood[size=0,age=" + age + "]");
			}

			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 50");
			context.waitTicks(10);
			context.takeScreenshot("gore-5-ages");
			server.runCommand("execute as @p at @s run fill ~-5 ~ ~1 ~5 ~ ~8 minecraft:air replace outbreak:blood");

			// 5. A legless crawler dragging itself along, leaving a smear.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 35");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~-3 ~ ~4 {NoAI:1b,PersistenceRequired:1b,Rotation:[-90f,0f],Tags:[\"drag\"]}");
			server.runCommand("outbreak walker sever @e[tag=drag] left_leg");
			server.runCommand("kill @e[type=outbreak:body_part]");
			for (int step = 0; step < 50; step++) {
				server.runCommand("execute as @e[tag=drag] at @s run tp @s ~0.12 ~ ~");
				context.waitTicks(1);
			}

			context.takeScreenshot("gore-6-trail");
			server.runCommand("kill @e[tag=drag]");
			context.waitTicks(110);

			// 6. A machete to the body: the spray.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:machete");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~2.2 {PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"hit\"]}");
			server.runCommand("attribute @e[tag=hit,limit=1] minecraft:movement_speed base set 0");
			server.runCommand("outbreak walker crawler @e[tag=hit] false");
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(2);
			context.takeScreenshot("gore-7-spray");
			context.waitTicks(30);
		}
	}
}
