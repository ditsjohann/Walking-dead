package io.github.ditsjohann.outbreak.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Screenshots of the dark-paper UI: HUD, health panel, the 18-slot inventory, every container screen and the new blocks. */
public class UiClientTest implements FabricClientGameTest {
	private static final String[][] CONTAINERS = {
		{"cupboard", "minecraft:chest[facing=north]"},
		{"crate", "minecraft:barrel[facing=north]"},
		{"fridge", "outbreak:fridge[facing=north]"},
		{"kitchen-cupboard", "outbreak:kitchen_cupboard[facing=north]"},
		{"toolbox", "outbreak:toolbox[facing=north]"},
		{"metal-cabinet", "outbreak:metal_cabinet[facing=north]"},
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
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
			server.runCommand("item replace entity @p hotbar.7 with outbreak:school_bag");
			server.runCommand("item replace entity @p hotbar.8 with outbreak:hiking_bag");
			server.runCommand("item replace entity @p weapon.offhand with minecraft:torch 12");
			server.runCommand("item replace entity @p inventory.0 with outbreak:nails 24");
			server.runCommand("item replace entity @p inventory.1 with minecraft:oak_planks 16");
			server.runCommand("item replace entity @p inventory.2 with outbreak:disinfectant");
			server.runCommand("item replace entity @p inventory.4 with minecraft:apple 3");
			server.runCommand("outbreak wound @p left_arm laceration");
			server.runCommand("outbreak wound @p right_leg scratch");
			server.runCommand("outbreak set @p thirst 0.65");
			server.runCommand("outbreak set @p fatigue 0.55");

			// The new containers in the world, next to a chest and a barrel.
			server.runCommand("execute as @p at @s run fill ~-4 ~ ~3 ~4 ~2 ~5 minecraft:air");
			server.runCommand("execute as @p at @s run fill ~-4 ~-1 ~3 ~4 ~-1 ~5 minecraft:oak_planks");
			server.runCommand("execute as @p at @s run setblock ~-3 ~ ~4 minecraft:chest[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~-2 ~ ~4 minecraft:barrel[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~-1 ~ ~4 outbreak:fridge[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~0 ~ ~4 outbreak:kitchen_cupboard[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~1 ~ ~4 outbreak:toolbox[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~2 ~ ~4 outbreak:metal_cabinet[facing=north]");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 20");
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(4));
			context.waitTicks(30);
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
			context.waitTicks(10);
			context.takeScreenshot("ui-1-hud");
			server.runCommand("execute as @p at @s run fill ~-4 ~ ~3 ~4 ~2 ~5 minecraft:air");

			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitTicks(10);
			screenshot(context, "ui-2-health-panel");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			screenshot(context, "ui-3-inventory");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// Every container, opened by right-clicking it like a player would (close by: the toolbox is small).
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 45");
			int shot = 4;
			for (String[] container : CONTAINERS) {
				server.runCommand("execute as @p at @s run fill ~-1 ~ ~1 ~1 ~1 ~2 minecraft:air");
				server.runCommand("execute as @p at @s run setblock ~ ~ ~1 " + container[1]);
				context.waitTicks(5);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(15);
				screenshot(context, "ui-" + shot++ + "-" + container[0]);
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitTicks(5);
			}

			// A double chest: the wardrobe screen.
			server.runCommand("execute as @p at @s run fill ~-1 ~ ~1 ~1 ~1 ~2 minecraft:air");
			server.runCommand("execute as @p at @s run setblock ~ ~ ~1 minecraft:chest[facing=north,type=left]");
			server.runCommand("execute as @p at @s run setblock ~1 ~ ~1 minecraft:chest[facing=north,type=right]");
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(15);
			screenshot(context, "ui-" + shot++ + "-wardrobe");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// The two bags, opened from the hand.
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -60");
			for (int slot : new int[] {7, 8}) {
				context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(slot));
				context.waitTicks(5);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(15);
				screenshot(context, "ui-" + shot++ + (slot == 7 ? "-school-bag" : "-hiking-bag"));
				context.getInput().pressKey(InputConstants.KEY_ESCAPE);
				context.waitTicks(5);
			}
		}
	}

	/** Opening a screen puts the mouse in the middle; move it to a corner so no slot shows a highlight or tooltip. */
	private static void screenshot(ClientGameTestContext context, String name) {
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		context.takeScreenshot(name);
	}
}
