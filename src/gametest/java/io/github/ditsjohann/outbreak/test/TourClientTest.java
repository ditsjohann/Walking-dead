package io.github.ditsjohann.outbreak.test;

import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.phys.Vec3;

/**
 * A screenshot tour of everything in the mod so far: a herd by day and by night, every moodle, the
 * health panel, all items, a walker breaking in, dying of the infection, and meeting yourself again.
 */
public class TourClientTest implements FabricClientGameTest {
	private static final int[][] HERD = {{-3, 7}, {-1, 6}, {1, 8}, {3, 7}, {0, 10}, {-2, 11}, {2, 12}, {-4, 13}, {4, 11}};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty normal");
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");

			// 1. A herd coming your way.
			for (int[] pos : HERD) {
				server.runCommand("execute as @p at @s run summon outbreak:walker ~" + pos[0] + " ~ ~" + pos[1] + " {NoAI:1b,PersistenceRequired:1b}");
			}

			server.runCommand("execute as @e[type=outbreak:walker] at @s facing entity @p eyes run tp @s ~ ~ ~ ~ ~");
			context.waitTicks(40);
			context.takeScreenshot("tour-01-herd");

			// 2. The same herd at night, by torchlight.
			server.runCommand("time set midnight");
			server.runCommand("execute as @p at @s run setblock ~-2 ~ ~3 minecraft:torch");
			server.runCommand("execute as @p at @s run setblock ~2 ~ ~4 minecraft:torch");
			context.waitTicks(40);
			context.takeScreenshot("tour-02-night");
			server.runCommand("kill @e[type=outbreak:walker]");
			server.runCommand("time set noon");

			// 3. Every moodle at once: wounds, one bandaged with a rag, thirst, fatigue, panic, fever, hunger, a heavy load.
			server.runCommand("clear @p");
			server.runCommand("outbreak cure @p");
			server.runCommand("effect give @p minecraft:hunger 3 255 true");
			context.waitTicks(70);
			server.runCommand("effect clear @p minecraft:hunger");
			server.runCommand("outbreak wound @p left_arm laceration");
			server.runCommand("outbreak wound @p right_leg bite");
			server.runCommand("outbreak wound @p head scratch");
			server.runCommand("outbreak wound @p torso laceration");
			server.runCommand("item replace entity @p hotbar.0 with outbreak:rag 4");
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(40);
			context.getInput().releaseKey(options -> options.keyUse);
			server.runCommand("outbreak set @p thirst 0.9");
			server.runCommand("outbreak set @p fatigue 0.9");
			server.runCommand("outbreak set @p panic 0.7");
			server.runCommand("outbreak infect @p 0.8");
			server.runCommand("damage @p 6 minecraft:generic");
			server.runCommand("give @p minecraft:cobblestone 160");
			context.waitTicks(50);
			context.takeScreenshot("tour-03-all-moodles");
			context.getInput().pressKey(InputConstants.KEY_H);
			context.waitTicks(20);
			context.takeScreenshot("tour-04-health-panel");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);

			// 4. Everything you can find.
			server.runCommand("outbreak cure @p");
			server.runCommand("effect give @p minecraft:instant_health 1 10 true");
			server.runCommand("clear @p");
			for (String item : new String[] {"outbreak:rag 8", "outbreak:bandage 3", "outbreak:disinfectant", "outbreak:boiled_water",
				"minecraft:potion[minecraft:potion_contents={potion:\"minecraft:water\"}]", "outbreak:kitchen_knife", "outbreak:baseball_bat",
				"outbreak:crowbar", "outbreak:machete", "outbreak:hammer", "outbreak:nails 32", "outbreak:school_bag", "outbreak:hiking_bag",
				"minecraft:iron_axe", "minecraft:stone_pickaxe", "minecraft:bread 4", "minecraft:oak_planks 16", "minecraft:white_wool 3"}) {
				server.runCommand("give @p " + item);
			}

			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(20);
			context.takeScreenshot("tour-05-inventory");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);

			// 5. Holed up in a brick house: a noise outside, and a walker smashes the window up to the barricade.
			server.runCommand("execute as @p at @s run fill ~-3 ~-1 ~-3 ~3 ~3 ~5 minecraft:bricks hollow");
			server.runCommand("execute as @p at @s run setblock ~ ~ ~5 minecraft:glass_pane");
			server.runCommand("execute as @p at @s run setblock ~ ~1 ~5 minecraft:glass_pane");
			server.runCommand("execute as @p at @s run setblock ~ ~ ~4 outbreak:barricade[facing=north,planks=2]");
			server.runCommand("execute as @p at @s run setblock ~ ~1 ~4 outbreak:barricade[facing=north,planks=3]");
			server.runCommand("execute as @p at @s run setblock ~2 ~ ~-2 minecraft:torch");
			server.runCommand("execute as @p at @s run setblock ~-2 ~ ~-2 minecraft:torch");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~9 {PersistenceRequired:1b}");
			server.runCommand("gamemode creative @p");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~-1 0 5");
			context.waitTicks(20);
			server.runCommand("execute as @p at @s run outbreak noise ~0.5 ~1 ~5.5 24");
			context.waitTicks(20 * 16);
			context.takeScreenshot("tour-06-break-in");
			server.runCommand("kill @e[type=outbreak:walker]");

			// 6. Infected: the fever runs its course.
			server.runCommand("gamemode survival @p");
			server.runCommand("outbreak cure @p");
			Vec3 deathPos = server.computeOnServer(s -> singleplayer.getConnection().getServerPlayer().position());
			server.runCommand("outbreak infect @p 0.99");
			context.waitFor(minecraft -> minecraft.gui.screen() instanceof DeathScreen, 20 * 60);
			context.waitTicks(20);
			context.takeScreenshot("tour-07-death");

			// 7. Thirty seconds later, someone gets back up.
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			server.runCommand("gamemode creative @p");
			context.waitTicks(20 * 32);
			// Stand a few steps away, facing north (yaw 180) towards where you died.
			server.runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f 180 8", deathPos.x, deathPos.y, deathPos.z + 2.8));
			// Hold your old self still for the photo.
			server.runCommand("execute as @e[type=outbreak:walker] run data merge entity @s {NoAI:1b}");
			server.runCommand(String.format(Locale.ROOT, "tp @e[type=outbreak:walker] %.2f %.2f %.2f", deathPos.x, deathPos.y, deathPos.z));
			server.runCommand("execute as @e[type=outbreak:walker] at @s facing entity @p eyes run tp @s ~ ~ ~ ~ ~");
			context.waitTicks(30);
			context.takeScreenshot("tour-08-you-turned");
		}
	}
}
