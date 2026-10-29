package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Screenshots of the outbreak HUD: at rest, lined up on a walker's head, running, and underwater. */
public class HudClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty normal");
			server.runCommand("time set 1500");
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @p");
			server.runCommand("outbreak cure @p");
			server.runCommand("item replace entity @p hotbar.0 with outbreak:baseball_bat[minecraft:damage=60]");
			server.runCommand("item replace entity @p hotbar.1 with outbreak:kitchen_knife");
			server.runCommand("item replace entity @p hotbar.2 with outbreak:hammer");
			server.runCommand("item replace entity @p hotbar.3 with outbreak:bandage 3");
			server.runCommand("item replace entity @p hotbar.4 with outbreak:rag 7");
			server.runCommand("item replace entity @p hotbar.5 with minecraft:bread 4");
			server.runCommand("item replace entity @p hotbar.6 with outbreak:boiled_water");
			server.runCommand("item replace entity @p hotbar.7 with minecraft:oak_planks 16");
			server.runCommand("item replace entity @p weapon.offhand with minecraft:torch 12");
			server.runCommand("outbreak wound @p left_arm scratch");
			server.runCommand("outbreak set @p thirst 0.45");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");

			// 1. At rest, just after switching to the bat (its name and condition pop up).
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(4));
			context.waitTicks(30);
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
			context.waitTicks(10);
			context.takeScreenshot("hud-1-at-rest");

			// 2. Lined up on a walker's head: the crosshair turns red.
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~2.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -3");
			context.waitTicks(20);
			context.takeScreenshot("hud-2-headshot");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 16");
			context.waitTicks(10);
			context.takeScreenshot("hud-3-body");
			server.runCommand("kill @e[type=outbreak:walker]");

			// 3. Running: the noise meter goes to Loud.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			context.getInput().holdKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keySprint);
			context.waitTicks(30);
			context.takeScreenshot("hud-4-running");
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(options -> options.keyUp);

			// 4. Underwater: a breath bar over the hotbar.
			server.runCommand("execute as @p at @s run fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:water");
			context.waitTicks(80);
			context.takeScreenshot("hud-5-underwater");
		}
	}
}
