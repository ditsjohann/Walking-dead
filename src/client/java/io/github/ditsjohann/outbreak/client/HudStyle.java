package io.github.ditsjohann.outbreak.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The look of the outbreak HUD: dark see-through panels, thin borders, a muted gold accent. */
final class HudStyle {
	static final int PANEL = 0xB0141210;
	static final int SLOT = 0x60000000;
	static final int BORDER = 0xFF2E2A26;
	static final int ACCENT = 0xFFD9C27A;
	static final int TEXT = 0xFFE6E0D6;
	static final int DIM = 0xFF8C857A;
	static final int DANGER = 0xFFD04030;
	static final int WATER = 0xFF4A8FD8;

	private HudStyle() {
	}

	static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, PANEL);
		graphics.outline(x, y, width, height, BORDER);
	}

	/** {@code color} with its alpha scaled by {@code alpha} (0..1). */
	static int fade(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
		return (a << 24) | (color & 0xFFFFFF);
	}
}
