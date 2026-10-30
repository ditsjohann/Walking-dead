package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;

/** Screenshots of the guns: each held and aimed in first person, held and aimed in third person, a head shot. */
public class GunClientTest implements FabricClientGameTest {
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
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			server.runCommand("item replace entity @p hotbar.1 with outbreak:shotgun[outbreak:rounds=6]");
			server.runCommand("item replace entity @p hotbar.2 with outbreak:hunting_rifle[outbreak:rounds=5]");
			server.runCommand("item replace entity @p hotbar.0 with outbreak:pistol[outbreak:rounds=15]");
			server.runCommand("item replace entity @p hotbar.3 with outbreak:ammo_9mm 30");
			server.runCommand("item replace entity @p hotbar.4 with outbreak:shotgun_shells 12");
			server.runCommand("item replace entity @p hotbar.5 with outbreak:rifle_rounds 10");
			server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~7 {NoAI:1b,PersistenceRequired:1b,Tags:[\"target\"],Rotation:[180f,0f]}");

			String[] guns = {"pistol", "shotgun", "rifle"};
			for (int slot = 0; slot < 3; slot++) {
				int selected = slot;
				context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(selected));
				context.waitTicks(10);
				context.takeScreenshot("guns-" + (slot * 2 + 1) + "-" + guns[slot] + "-held");
				context.getInput().holdKey(options -> options.keyUse);
				context.waitTicks(15);
				context.takeScreenshot("guns-" + (slot * 2 + 2) + "-" + guns[slot] + "-aimed");
				context.getInput().releaseKey(options -> options.keyUse);
				context.waitTicks(5);
			}

			// Third person, from the side: holding the shotgun, then aiming it.
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(1));
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ -60 0");
			context.waitTicks(10);
			context.takeScreenshot("guns-7-third-person-held");
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(10);
			context.takeScreenshot("guns-8-third-person-aimed");
			context.getInput().releaseKey(options -> options.keyUse);
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

			// A head shot with the pistol, aimed: the muzzle flash, the blood, the count going down.
			context.runOnClient(minecraft -> minecraft.player.getInventory().setSelectedSlot(0));
			server.runCommand("execute as @p at @s anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=target,limit=1] eyes");
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(10);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(1);
			context.takeScreenshot("guns-9-head-shot");
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTicks(30);
			context.takeScreenshot("guns-10-down");
		}
	}
}
