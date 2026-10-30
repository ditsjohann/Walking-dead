package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Screenshots of the world winding down: a street at night with its lamps and lit windows, then the
 * power going; an NL-Alert; the radio; a fuel pump; and the sandbox settings for the water and power.
 */
public class WorldClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @p");
			server.runCommand("outbreak cure @p");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 5");

			// A street: asphalt, a row of houses with ceiling lights behind their windows, street lamps.
			server.runCommand("execute as @p at @s run fill ~-12 ~-1 ~2 ~12 ~-1 ~20 minecraft:gray_concrete_powder");
			server.runCommand("execute as @p at @s run fill ~-12 ~ ~14 ~12 ~5 ~20 minecraft:bricks");
			server.runCommand("execute as @p at @s run fill ~-11 ~ ~15 ~11 ~4 ~19 minecraft:air");
			for (int x = -10; x <= 10; x += 4) {
				server.runCommand("execute as @p at @s run fill ~" + x + " ~1 ~14 ~" + (x + 1) + " ~3 ~14 minecraft:glass_pane");
				server.runCommand("execute as @p at @s run setblock ~" + x + " ~4 ~17 minecraft:glowstone");
			}

			for (int x = -9; x <= 9; x += 9) {
				server.runCommand("execute as @p at @s run setblock ~" + x + " ~ ~11 minecraft:smooth_stone");
				server.runCommand("execute as @p at @s run fill ~" + x + " ~1 ~11 ~" + x + " ~3 ~11 minecraft:stone_brick_wall");
				server.runCommand("execute as @p at @s run setblock ~" + x + " ~4 ~11 minecraft:redstone_lamp[lit=true]");
			}

			server.runCommand("time set 18000");
			context.waitTicks(60);
			context.takeScreenshot("world-1-street-lit");

			// The power goes.
			server.runCommand("outbreak timeline power off");
			context.waitTicks(40);
			context.takeScreenshot("world-2-power-out");

			// An NL-Alert, in daylight.
			server.runCommand("time set 1500");
			server.runCommand("outbreak timeline alert 0");
			context.waitTicks(30);
			context.takeScreenshot("world-3-nl-alert");
			context.waitTicks(20 * 14);

			// The radio: switch it on.
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:portable_radio");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -30");
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyChat);
			context.waitTicks(5);
			context.takeScreenshot("world-4-radio");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// A fuel pump at a petrol station, as Arnis builds them; without power it's dead.
			server.runCommand("execute as @p at @s run fill ~-2 ~ ~4 ~2 ~ ~4 minecraft:smooth_stone_slab");
			server.runCommand("execute as @p at @s run setblock ~ ~ ~4 minecraft:white_concrete");
			server.runCommand("execute as @p at @s run setblock ~ ~1 ~4 minecraft:red_concrete");
			server.runCommand("execute as @p at @s run setblock ~ ~2 ~4 minecraft:smooth_stone_slab");
			server.runCommand("execute as @p at @s run setblock ~ ~1 ~3 minecraft:tripwire_hook[facing=north]");
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:jerrycan");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ facing ~ ~1.2 ~3.4");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			context.takeScreenshot("world-5-pump-dead");

			// Covered in walker guts: seen from the front, and the moodle.
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:walker_guts");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~-6 180 10");
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(50);
			context.getInput().releaseKey(options -> options.keyUse);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("world-7-guts");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));

			// The helicopter: it finds you and circles low overhead.
			server.runCommand("execute as @p run outbreak event helicopter");
			context.waitTicks(20 * 20);
			context.runOnClient(minecraft -> {
				var heli = minecraft.level.getEntitiesOfClass(io.github.ditsjohann.outbreak.world.Helicopter.class,
					minecraft.player.getBoundingBox().inflate(200.0)).stream().findFirst();
				heli.ifPresent(h -> {
					var to = h.position().subtract(minecraft.player.getEyePosition());
					minecraft.player.setYRot((float) (Math.atan2(to.z, to.x) * 180.0 / Math.PI) - 90.0F);
					minecraft.player.setXRot((float) (-Math.atan2(to.y, Math.hypot(to.x, to.z)) * 180.0 / Math.PI) + 8.0F);
				});
			});
			context.waitTicks(2);
			context.takeScreenshot("world-8-helicopter");
			server.runCommand("kill @e[type=outbreak:helicopter]");

			// A flare over the Euroborg at dusk, a long way off.
			server.runCommand("time set 13500");
			server.runCommand("execute as @p run outbreak event flare");
			context.waitTicks(3);
			context.runOnClient(minecraft -> {
				// Look the way of the flare: the Euroborg of the default map box is far east and south of here.
				minecraft.player.setYRot(-52.0F);
				minecraft.player.setXRot(-6.0F);
			});
			context.waitTicks(45);
			context.takeScreenshot("world-9-flare");

			// The sandbox settings, with when the water and the power go.
			server.runCommand("execute as @p run outbreak sandbox");
			context.waitTicks(20);
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(2);
			context.takeScreenshot("world-6-sandbox");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
		}
	}
}
