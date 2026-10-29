package io.github.ditsjohann.outbreak.client;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.survivor.Moodle;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Replaces hearts, hunger and armour with Project Zomboid-style moodles (top right) and a small body
 * doll showing wounds (bottom left).
 */
final class SurvivorHud {
	private static final int MOODLE_SIZE = 20;
	private static final int[] LEVEL_COLORS = {0, 0xB0B89A3A, 0xB0D0702A, 0xB0C03028, 0xD08B0000};

	private SurvivorHud() {
	}

	static void register() {
		HudElementRegistry.removeElement(VanillaHudElements.HEALTH_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.FOOD_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.ARMOR_BAR);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Outbreak.id("survivor"), SurvivorHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}

		SurvivorState state = Survivors.get(player);
		drawMoodles(graphics, player, state, graphics.guiWidth() - MOODLE_SIZE - 6, 6);
		BodyDoll.draw(graphics, state, 8, graphics.guiHeight() - BodyDoll.HEIGHT * 2 - 8, 2);
	}

	/** Draws the active moodles in a column; returns how many were drawn. */
	static int drawMoodles(GuiGraphicsExtractor graphics, LocalPlayer player, SurvivorState state, int x, int y) {
		int drawn = 0;
		for (Moodle moodle : Moodle.values()) {
			int level = moodle.level(player, state);
			if (level <= 0) {
				continue;
			}

			int top = y + drawn * (MOODLE_SIZE + 2);
			int color = LEVEL_COLORS[level];
			if (level == 4 && (Util.getMillis() / 500) % 2 == 0) {
				color = 0xF0D01010;
			}

			graphics.fill(x, top, x + MOODLE_SIZE, top + MOODLE_SIZE, color);
			graphics.outline(x, top, MOODLE_SIZE, MOODLE_SIZE, 0xFF1E1A18);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(moodle), x + 2, top + 2, 16, 16);
			drawn++;
		}

		return drawn;
	}

	static Identifier sprite(Moodle moodle) {
		return Outbreak.id("moodle/" + moodle.id());
	}
}
