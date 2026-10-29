package io.github.ditsjohann.outbreak.test;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;

/** Screenshots for phase 4: every vehicle, a wreck, sitting in a car, driving, the mechanics page and a trunk. */
public class VehicleClientTest implements FabricClientGameTest {
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
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");
			// An Arnis-style road: the asphalt mix with a white line down the middle.
			server.runCommand("execute as @p at @s run fill ~-22 ~-1 ~-8 ~22 ~-1 ~40 minecraft:gray_concrete_powder");
			server.runCommand("execute as @p at @s run fill ~-22 ~-1 ~10 ~22 ~-1 ~10 minecraft:white_concrete");

			// 1. The line-up, side on: bicycle, scooter, hatchback, sedan, van and a burnt-out hatchback.
			String[][] lineup = {{"-15", "bicycle", "new"}, {"-11", "scooter", "new"}, {"-6", "hatchback", "new"},
				{"0", "sedan", "new"}, {"7", "van", "new"}, {"14", "hatchback", "wreck"}};
			for (String[] vehicle : lineup) {
				server.runCommand("execute as @p at @s positioned ~" + vehicle[0] + " ~ ~14 rotated 90 0 run outbreak vehicle spawn "
					+ vehicle[1] + " " + vehicle[2]);
			}

			context.waitTicks(40);
			context.takeScreenshot("vehicles-1-lineup");
			server.runCommand("execute as @p at @s positioned ~ ~ ~14 run kill @e[type=outbreak:bicycle,distance=..20]");

			// 2. In the driver's seat of our own hatchback (with its key), first person.
			server.runCommand("execute as @p at @s positioned ~ ~ ~3.5 rotated 90 0 run outbreak vehicle spawn hatchback new true");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 25");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			context.takeScreenshot("vehicles-2-drivers-seat");

			// 3. Start the engine (R) and drive off, seen from behind, through three walkers standing in the road.
			for (int ahead : new int[] {6, 8, 10}) {
				server.runCommand("execute as @e[type=outbreak:hatchback,sort=nearest,limit=1] at @s rotated as @s run summon outbreak:walker ^ ^ ^"
					+ ahead + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"roadkill\"]}");
			}

			context.getInput().pressKey(InputConstants.KEY_R);
			context.waitTicks(20);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(50);
			// Look back over your shoulder a bit, for a three-quarter view.
			context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 50.0F));
			context.getInput().holdKey(options -> options.keyLeft);
			context.waitTicks(10);
			context.takeScreenshot("vehicles-3-driving");
			context.getInput().releaseKey(options -> options.keyLeft);
			context.getInput().releaseKey(options -> options.keyUp);
			server.runCommand("execute if entity @e[tag=roadkill] run say Walkers still standing after the drive");
			server.runCommand("execute unless entity @e[tag=roadkill] run say All three walkers run over");
			context.getInput().holdKey(options -> options.keyDown);
			context.waitTicks(40);
			context.getInput().releaseKey(options -> options.keyDown);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

			// 4. Out, and under the hood: the mechanics page (sneak + right-click), with a wrench and a jack.
			context.getInput().pressKey(options -> options.keyShift);
			context.waitTicks(10);
			server.runCommand("item replace entity @p hotbar.1 with outbreak:wrench");
			server.runCommand("item replace entity @p hotbar.2 with outbreak:jack");
			server.runCommand("item replace entity @p hotbar.3 with outbreak:tire[minecraft:damage=40]");
			server.runCommand("execute as @p at @s run tp @s @e[type=outbreak:hatchback,sort=nearest,limit=1]");
			server.runCommand("execute as @p at @s run tp @s ^ ^ ^-3");
			server.runCommand("execute as @p at @s facing entity @e[type=outbreak:hatchback,sort=nearest,limit=1] feet run tp @s ~ ~ ~ ~ 25");
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(4));
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(3);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(15);
			screenshot(context, "vehicles-4-mechanics");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// 5. The van's cargo space: right-click its back doors.
			server.runCommand("execute as @e[type=outbreak:van,limit=1] at @s run tp @p ^ ^ ^-4 ~ 20");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(15);
			screenshot(context, "vehicles-5-van-cargo");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// 6. On the bicycle.
			server.runCommand("execute as @p at @s positioned ~ ~ ~3 rotated 0 0 run outbreak vehicle spawn bicycle new");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 30");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(30);
			context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 80.0F));
			context.waitTicks(2);
			context.takeScreenshot("vehicles-6-bicycle");
			context.getInput().releaseKey(options -> options.keyUp);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}

	private static void screenshot(ClientGameTestContext context, String name) {
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(2);
		context.takeScreenshot(name);
	}
}
