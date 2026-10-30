package io.github.ditsjohann.outbreak.test;

import io.github.ditsjohann.outbreak.client.menu.NewSurvivorScreen;
import io.github.ditsjohann.outbreak.client.menu.SandboxScreen;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Screenshots of phase 5: the title screen, the new survivor screens, making a survivor (a nurse,
 * some traits), arriving, and the character sheet. Clicks go where the screens put things (the
 * pages are centred, so positions are worked out from the GUI size).
 */
public class CharacterClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// 1. The title screen (ours replaces Minecraft's).
		context.waitTicks(40);
		context.takeScreenshot("menu-1-title");

		// 2. A new survivor: a week later.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new NewSurvivorScreen(minecraft.gui.screen())));
		context.waitTicks(5);
		int[] page = this.page(context, 400, 226);
		this.click(context, page[0] + 14 + 86, page[1] + 50 + 22 + 10);
		context.waitTicks(5);
		context.takeScreenshot("menu-2-new-survivor");

		// 3. The sandbox settings, on a nightmare.
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new SandboxScreen(minecraft.gui.screen(),
			SandboxSettings.DEFAULT.withPreset(SandboxSettings.Preset.NIGHTMARE), settings -> { })));
		context.waitTicks(5);
		context.takeScreenshot("menu-3-sandbox");
		context.runOnClient(minecraft -> minecraft.gui.setScreen(new TitleScreen()));
		context.waitTicks(5);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			singleplayer.getConnection().waitForChunksRender();
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 1000");
			server.runCommand("gamemode survival @a");

			// 4. Test players get a plain survivor; take it away and the character screen comes up.
			server.runCommand("outbreak character reset @p");
			context.waitTicks(20);
			context.takeScreenshot("character-1-new");

			// 5. A nurse: lucky and a fast healer, paid for by being a slow learner and out of shape.
			int[] dossier = this.page(context, 420, 232);
			int left = dossier[0];
			int top = dossier[1];
			this.click(context, left + 60, top + 80 + 13 + 5);
			for (int trait : new int[] {3, 6, 13, 12}) {
				this.click(context, left + 200, top + 50 + trait * 11 + 5);
			}

			context.getInput().setCursorPos(this.scale(context) * (left + 200), this.scale(context) * (top + 50 + 5 * 11 + 5));
			context.waitTicks(5);
			context.takeScreenshot("character-2-nurse");

			// 6. Begin: off to the Grote Markt (on a flat test world, just where it would be).
			this.click(context, left + 294 + 58, top + 192 + 10);
			context.waitTicks(40);
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("character-3-arrived");

			// 7. Some practice, then the character sheet (K).
			server.runCommand("outbreak character xp @p first_aid 900");
			server.runCommand("outbreak character xp @p fighting 300");
			server.runCommand("outbreak character xp @p fitness 260");
			server.runCommand("outbreak character xp @p carpentry 40");
			context.waitTicks(10);
			context.getInput().pressKey(InputConstants.KEY_K);
			context.waitTicks(5);
			context.takeScreenshot("character-4-sheet");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// 8. The operator's sandbox editor, in game.
			server.runCommand("outbreak sandbox preset survivor");
			context.waitTicks(5);
			server.runCommand("execute as @p run outbreak sandbox");
			context.waitTicks(10);
			context.takeScreenshot("menu-4-sandbox-in-game");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
		}
	}

	private double scale(ClientGameTestContext context) {
		return context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
	}

	/** Top left corner of a centred page of this size. */
	private int[] page(ClientGameTestContext context, int width, int height) {
		return context.computeOnClient(minecraft -> {
			int w = minecraft.getWindow().getGuiScaledWidth();
			int h = minecraft.getWindow().getGuiScaledHeight();
			return new int[] {(w - width) / 2, Math.max(0, (h - height) / 2)};
		});
	}

	private void click(ClientGameTestContext context, int guiX, int guiY) {
		double scale = this.scale(context);
		context.getInput().setCursorPos(guiX * scale, guiY * scale);
		context.waitTicks(1);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}
}
