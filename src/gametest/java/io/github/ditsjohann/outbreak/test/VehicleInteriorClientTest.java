package io.github.ditsjohann.outbreak.test;

import io.github.ditsjohann.outbreak.client.vehicle.VehicleRenderer;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Screenshots of the vehicle upgrade: the detailed cars and wheels, sitting at the dashboard, clicking
 * the ignition (the key goes in and turns), hands on the wheel while driving, the driver from
 * outside, and riding the bicycle and the moped.
 */
public class VehicleInteriorClientTest implements FabricClientGameTest {
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
			server.runCommand("execute as @p at @s run fill ~-22 ~-1 ~-8 ~22 ~-1 ~40 minecraft:gray_concrete_powder");
			server.runCommand("execute as @p at @s run summon minecraft:marker ~ ~ ~ {Tags:[\"home\"]}");

			// 1. Three-quarter view of each car, then the wheels close up.
			server.runCommand("execute as @p at @s positioned ~-6 ~ ~9 rotated 135 0 run outbreak vehicle spawn hatchback new");
			server.runCommand("execute as @p at @s positioned ~0 ~ ~10 rotated 135 0 run outbreak vehicle spawn sedan new");
			server.runCommand("execute as @p at @s positioned ~7 ~ ~11 rotated 135 0 run outbreak vehicle spawn van new");
			server.runCommand("execute as @p at @s positioned ~3.5 ~ ~5 rotated 135 0 run outbreak vehicle spawn scooter new");
			server.runCommand("execute as @p at @s positioned ~-2.5 ~ ~5 rotated 135 0 run outbreak vehicle spawn bicycle new");
			server.runCommand("execute as @p at @s run tp @s ~ ~2 ~ 0 12");
			context.waitTicks(40);
			context.takeScreenshot("interior-1-cars");
			server.runCommand("execute as @e[type=outbreak:sedan,limit=1] at @s run tp @p ^-2.6 ^0.2 ^1.6 ~-90 25");
			context.waitTicks(10);
			context.takeScreenshot("interior-2-wheel");
			server.runCommand("execute as @e[type=outbreak:hatchback,limit=1] at @s run tp @p ^-4 ^1.2 ^2 ~-120 15");
			context.waitTicks(10);
			context.takeScreenshot("interior-3-hatchback-side");
			server.runCommand("kill @e[type=outbreak:sedan]");
			server.runCommand("kill @e[type=outbreak:van]");
			server.runCommand("kill @e[type=outbreak:hatchback]");
			server.runCommand("kill @e[type=outbreak:scooter]");
			server.runCommand("kill @e[type=outbreak:bicycle]");

			// 2. In the driver's seat of our own hatchback, its key in our pocket.
			server.runCommand("kill @e[type=item]");
			server.runCommand("tp @p @e[tag=home,limit=1]");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 10");
			server.runCommand("execute as @p at @s positioned ~ ~ ~3.5 rotated 90 0 run outbreak vehicle spawn hatchback new true");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 25");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			context.runOnClient(minecraft -> minecraft.player.setXRot(8.0F));
			context.waitTicks(3);
			context.takeScreenshot("interior-4-dashboard");

			// 3. Look at the ignition: the note says what a click does. Right-click: the key goes in and turns.
			context.runOnClient(VehicleInteriorClientTest::lookAtIgnition);
			context.waitTicks(3);
			context.takeScreenshot("interior-5-ignition");
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(25);
			context.runOnClient(VehicleInteriorClientTest::lookAtIgnition);
			context.waitTicks(2);
			context.takeScreenshot("interior-6-started");
			server.runCommand("execute as @p run outbreak vehicle info");

			// 4. Driving off, turning: hands on the wheel, the wheel turned.
			context.runOnClient(minecraft -> minecraft.player.setXRot(10.0F));
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(30);
			context.getInput().holdKey(options -> options.keyLeft);
			context.waitTicks(6);
			context.takeScreenshot("interior-7-driving");
			context.getInput().releaseKey(options -> options.keyLeft);
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().holdKey(options -> options.keyDown);
			context.waitTicks(30);
			context.getInput().releaseKey(options -> options.keyDown);
			context.waitTicks(30);

			// 5. The driver from outside.
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() - 100.0F));
			context.runOnClient(minecraft -> minecraft.player.setXRot(12.0F));
			context.waitTicks(5);
			context.takeScreenshot("interior-8-driver-outside");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

			// 6. Left-click the ignition: the engine stops and the key comes out.
			context.runOnClient(VehicleInteriorClientTest::lookAtIgnition);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(10);
			server.runCommand("execute as @p run outbreak vehicle info");
			server.runCommand("execute if items entity @p container.* outbreak:car_key run say The key is back in my pocket");
			context.getInput().pressKey(options -> options.keyShift);
			context.waitTicks(10);
			server.runCommand("kill @e[type=outbreak:hatchback]");
			server.runCommand("kill @e[type=item]");
			server.runCommand("tp @p @e[tag=home,limit=1]");

			// 7. The bicycle: pedalling, seen from the side, and the handlebars in first person.
			server.runCommand("execute as @p at @s positioned ~ ~ ~3 rotated 90 0 run outbreak vehicle spawn bicycle new");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 30");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(15);
			// Steering left: the handlebars turn left.
			context.getInput().holdKey(options -> options.keyLeft);
			context.waitTicks(4);
			context.runOnClient(minecraft -> minecraft.player.setXRot(40.0F));
			context.takeScreenshot("interior-9-bicycle-first-person");
			context.getInput().releaseKey(options -> options.keyLeft);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 90.0F));
			context.waitTicks(3);
			context.takeScreenshot("interior-10-bicycle");
			context.getInput().releaseKey(options -> options.keyUp);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyShift);
			context.waitTicks(10);
			server.runCommand("kill @e[type=outbreak:bicycle]");
			server.runCommand("kill @e[type=item]");
			server.runCommand("tp @p @e[tag=home,limit=1]");

			// 8. The moped.
			server.runCommand("execute as @p at @s positioned ~ ~ ~3 rotated 90 0 run outbreak vehicle spawn scooter new true");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 30");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.runOnClient(VehicleInteriorClientTest::lookAtIgnition);
			context.waitTicks(2);
			context.takeScreenshot("interior-11-moped-first-person");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 110.0F));
			context.runOnClient(minecraft -> minecraft.player.setXRot(15.0F));
			context.waitTicks(3);
			context.takeScreenshot("interior-12-moped");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			context.getInput().pressKey(options -> options.keyShift);
			context.waitTicks(10);
			server.runCommand("kill @e[type=outbreak:scooter]");
			server.runCommand("kill @e[type=item]");
			server.runCommand("tp @p @e[tag=home,limit=1]");

			// 9. The van's cab.
			server.runCommand("execute as @p at @s positioned ~ ~ ~3.5 rotated 90 0 run outbreak vehicle spawn van new true");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 25");
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(15);
			context.runOnClient(minecraft -> minecraft.player.setXRot(10.0F));
			context.waitTicks(2);
			context.takeScreenshot("interior-13-van");
			context.getInput().pressKey(options -> options.keyShift);
			context.waitTicks(10);
		}
	}

	/** Turns your head to look straight at the ignition of what you're sitting in. */
	private static void lookAtIgnition(Minecraft minecraft) {
		if (!(minecraft.player.getVehicle() instanceof Vehicle vehicle)) {
			return;
		}

		Vec3 ignition = VehicleRenderer.ignition(vehicle, 1.0F);
		if (ignition == null) {
			return;
		}

		Vec3 to = ignition.subtract(minecraft.player.getEyePosition());
		minecraft.player.setYRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
		minecraft.player.setXRot((float) (-Mth.atan2(to.y, Math.hypot(to.x, to.z)) * Mth.RAD_TO_DEG));
	}
}
