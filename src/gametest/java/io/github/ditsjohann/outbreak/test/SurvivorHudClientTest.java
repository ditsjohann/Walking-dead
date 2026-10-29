package io.github.ditsjohann.outbreak.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Builds a test world, gives the player some wounds and needs, puts a walker in front of them and
 * takes screenshots of the survivor HUD and the health panel (run/screenshots).
 */
public class SurvivorHudClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();

			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty normal");
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @p at @s run summon outbreak:walker ^ ^ ^4 {NoAI:1b,PersistenceRequired:1b}");
			server.runCommand("execute as @p at @s run tp @e[type=outbreak:walker] ^ ^ ^4 facing entity @p");
			server.runCommand("outbreak wound @p left_arm laceration");
			server.runCommand("outbreak wound @p right_leg bite");
			server.runCommand("outbreak wound @p torso scratch");
			server.runCommand("outbreak set @p thirst 0.7");
			server.runCommand("outbreak set @p fatigue 0.75");
			server.runCommand("give @p outbreak:rag 6");
			server.runCommand("give @p outbreak:bandage 2");
			server.runCommand("give @p outbreak:disinfectant");
			server.runCommand("give @p outbreak:boiled_water");
			context.waitTicks(60);
			context.takeScreenshot("phase2-hud");

			// Bandage the worst wound with a rag in the first free hotbar slot, the way a player would.
			server.runCommand("item replace entity @p hotbar.0 with outbreak:rag 6");
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(40);
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTicks(10);

			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitTicks(20);
			context.takeScreenshot("phase2-health-panel");
		}
	}
}
