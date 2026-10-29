package io.github.ditsjohann.outbreak.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;

/**
 * Screenshots of real fights: a bat to the body and to the head (seen from behind the player), bare
 * fists, the swing of each kind of weapon, and the bat held two-handed. The walkers have their AI
 * but can't walk (speed 0), so hits still knock them about; the player is in creative so they
 * don't fight back.
 */
public class CombatClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 1500");
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");

			// 1. The bat, to the body: it reels away to your left.
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:baseball_bat");
			this.walker(server, "body");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 16");
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyAttack);
			this.lookAside(context);
			for (int tick : new int[] {2, 3, 5}) {
				context.waitTicks(tick == 2 ? 1 : tick == 3 ? 1 : 2);
				context.takeScreenshot("combat-1-bat-body-" + tick);
			}

			context.waitTicks(40);
			server.runCommand("kill @e[tag=body]");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(100);

			// 2. The bat, to the head: dead, and down it goes.
			this.walker(server, "head");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 -4");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyAttack);
			this.lookAside(context);
			context.waitTicks(2);
			context.takeScreenshot("combat-2-bat-head-3");
			context.waitTicks(6);
			context.takeScreenshot("combat-2-bat-head-9");
			context.waitTicks(15);
			context.takeScreenshot("combat-2-bat-head-24");
			context.waitTicks(100);

			server.runCommand("kill @e[tag=head]");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			// 3. Bare fists, first person: left, then right.
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			this.walker(server, "fists");
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 14");
			context.waitTicks(20);
			for (int punch = 1; punch <= 2; punch++) {
				context.getInput().pressKey(options -> options.keyAttack);
				context.waitTicks(2);
				context.takeScreenshot("combat-3-punch-" + punch);
				context.waitTicks(12);
			}

			server.runCommand("kill @e[tag=fists]");
			context.waitTicks(100);

			// 4. Each kind of swing, first person, at its peak.
			String[][] swings = {{"baseball_bat", "3"}, {"hammer", "2"}, {"kitchen_knife", "2"}};
			for (String[] swing : swings) {
				server.runCommand("item replace entity @p weapon.mainhand with outbreak:" + swing[0]);
				context.waitTicks(30);
				context.getInput().pressKey(options -> options.keyAttack);
				context.waitTicks(Integer.parseInt(swing[1]));
				context.takeScreenshot("combat-4-swing-" + swing[0]);
			}

			// 5. Third person, from the front: the bat held two-handed, then mid-swing.
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:baseball_bat");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 0");
			context.waitTicks(30);
			context.takeScreenshot("combat-5-bat-ready");
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(1);
			context.takeScreenshot("combat-5-bat-windup");
			context.waitTicks(2);
			context.takeScreenshot("combat-5-bat-sweep");
			server.runCommand("item replace entity @p weapon.mainhand with outbreak:hammer");
			context.waitTicks(30);
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(1);
			context.takeScreenshot("combat-5-hammer-raised");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}

	/** Once the hit's in, turn the player (and the camera behind them) so the walker isn't hidden behind them. */
	private void lookAside(ClientGameTestContext context) {
		context.waitTicks(1);
		context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 40.0F));
	}

	/** A walker 2.2 blocks in front of the player, facing them, unable to walk. */
	private void walker(TestServerContext server, String tag) {
		server.runCommand("execute as @p at @s run summon outbreak:walker ~ ~ ~2.2 {PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"" + tag + "\"]}");
		server.runCommand("attribute @e[tag=" + tag + ",limit=1] minecraft:movement_speed base set 0");
		server.runCommand("outbreak walker crawler @e[tag=" + tag + "] false");
	}
}
