package io.github.ditsjohann.outbreak.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Screenshots for phase 3: barricades, weapons and a heavy load, an open bag, and a looted kitchen chest. */
public class ScavengingClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			server.runCommand("outbreak cure @p");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");

			// A wall with three windows, barricaded with 1, 2 and 4 planks.
			server.runCommand("execute as @p at @s run fill ~-4 ~ ~4 ~4 ~2 ~4 minecraft:bricks");
			for (int x : new int[] {-2, 0, 2}) {
				server.runCommand("execute as @p at @s run setblock ~" + x + " ~1 ~4 minecraft:glass_pane");
			}

			server.runCommand("execute as @p at @s run setblock ~-2 ~1 ~3 outbreak:barricade[facing=north,planks=1]");
			server.runCommand("execute as @p at @s run setblock ~0 ~1 ~3 outbreak:barricade[facing=north,planks=2]");
			server.runCommand("execute as @p at @s run setblock ~2 ~1 ~3 outbreak:barricade[facing=north,planks=4]");

			server.runCommand("clear @p");
			server.runCommand("item replace entity @p hotbar.0 with outbreak:kitchen_knife");
			server.runCommand("item replace entity @p hotbar.1 with outbreak:baseball_bat");
			server.runCommand("item replace entity @p hotbar.2 with outbreak:crowbar");
			server.runCommand("item replace entity @p hotbar.3 with outbreak:machete");
			server.runCommand("item replace entity @p hotbar.4 with outbreak:hammer");
			server.runCommand("item replace entity @p hotbar.5 with outbreak:nails 24");
			server.runCommand("item replace entity @p hotbar.6 with minecraft:oak_planks 12");
			server.runCommand("item replace entity @p hotbar.7 with outbreak:school_bag");
			server.runCommand("item replace entity @p hotbar.8 with outbreak:hiking_bag");
			server.runCommand("give @p minecraft:cobblestone 192");
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(1));
			context.waitTicks(60);
			context.takeScreenshot("phase3-barricades-weapons-heavy");

			// A worn hiking bag with some loot in it, opened with B.
			server.runCommand("clear @p minecraft:cobblestone");
			server.runCommand("item replace entity @p armor.chest with outbreak:hiking_bag[minecraft:container=["
				+ "{slot:0,item:{id:\"minecraft:bread\",count:3}},{slot:1,item:{id:\"outbreak:rag\",count:5}},"
				+ "{slot:2,item:{id:\"outbreak:bandage\",count:2}},{slot:9,item:{id:\"minecraft:cobblestone\",count:64}}]]");
			context.waitTicks(20);
			context.getInput().pressKey(InputConstants.KEY_B);
			context.waitTicks(20);
			context.takeScreenshot("phase3-worn-bag");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(10);

			// A kitchen: a chest between a furnace and a smoker, filled the first time it's opened.
			server.runCommand("execute as @p at @s run fill ~-1 ~ ~2 ~1 ~ ~2 minecraft:air");
			server.runCommand("execute as @p at @s run setblock ~ ~ ~2 minecraft:chest[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~-1 ~ ~2 minecraft:furnace[facing=north]");
			server.runCommand("execute as @p at @s run setblock ~1 ~ ~2 minecraft:smoker[facing=north]");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 40");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			context.takeScreenshot("phase3-kitchen-loot");
		}
	}
}
