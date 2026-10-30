package io.github.ditsjohann.outbreak.client.menu;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.PaperStyle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** The menus' pages: sheets of dark paper (tools/menu/make_menu.py), written on in ink and pencil. */
public record Paper(Identifier texture, int width, int height) {
	public static final Paper NEW_SURVIVOR = new Paper(Outbreak.id("textures/gui/new_survivor.png"), 400, 226);
	public static final Paper DOSSIER = new Paper(Outbreak.id("textures/gui/dossier.png"), 420, 232);
	public static final Paper CHARACTER_SHEET = new Paper(Outbreak.id("textures/gui/character_sheet.png"), 300, 200);

	public static final int INK = PaperStyle.INK;
	public static final int PENCIL = PaperStyle.PENCIL;
	public static final int RED = PaperStyle.MARKER_RED;
	public static final int GREEN = 0xFF2E5A1E;

	public int left(int screenWidth) {
		return (screenWidth - this.width) / 2;
	}

	public int top(int screenHeight) {
		return Math.max(0, (screenHeight - this.height) / 2);
	}

	public void draw(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, this.left(screenWidth), this.top(screenHeight), 0.0F, 0.0F, this.width, this.height, this.width, this.height);
	}

	/** A heading, underlined in pencil. */
	public static void heading(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y) {
		graphics.text(font, text, x, y, INK, false);
		graphics.fill(x, y + 10, x + font.width(text) + 4, y + 11, PENCIL);
	}

	/** Text wrapped to a width; returns the y under it. */
	public static int wrapped(GuiGraphicsExtractor graphics, Font font, FormattedText text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			graphics.text(font, line, x, y, color, false);
			y += 10;
		}

		return y;
	}
}
