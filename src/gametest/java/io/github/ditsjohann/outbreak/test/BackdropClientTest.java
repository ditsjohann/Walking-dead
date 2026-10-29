package io.github.ditsjohann.outbreak.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Clean backdrops (HUD hidden with F1) for HUD art mockups (tools/textures/hudart.py). */
public class BackdropClientTest implements FabricClientGameTest {
	private static final int[][] HERD = {{-3, 7}, {-1, 6}, {1, 8}, {3, 7}, {0, 10}, {-2, 11}, {2, 12}, {-4, 13}, {4, 11}};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 13000");
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			for (int[] pos : HERD) {
				server.runCommand("execute as @p at @s run summon outbreak:walker ~" + pos[0] + " ~ ~" + pos[1] + " {NoAI:1b,PersistenceRequired:1b}");
			}

			server.runCommand("execute as @e[type=outbreak:walker] at @s facing entity @p eyes run tp @s ~ ~ ~ ~ ~");
			context.getInput().pressKey(InputConstants.KEY_F1);
			context.waitTicks(40);
			context.takeScreenshot("backdrop-herd");
		}
	}
}
