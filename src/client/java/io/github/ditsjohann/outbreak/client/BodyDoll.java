package io.github.ditsjohann.outbreak.client;

import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/** A little body silhouette coloured by wounds, drawn from rectangles (Minecraft skin proportions). */
final class BodyDoll {
	static final int WIDTH = 16;
	static final int HEIGHT = 32;

	private static final int HEALTHY = 0xFF948666;
	private static final int OUTLINE = 0xFF26201A;

	private BodyDoll() {
	}

	/** Draws the doll with its top-left corner at (x, y), each doll pixel {@code scale} screen pixels. */
	static void draw(GuiGraphicsExtractor graphics, SurvivorState state, int x, int y, int scale) {
		part(graphics, state, BodyPart.HEAD, x, y, 4, 0, 8, 8, scale);
		part(graphics, state, BodyPart.TORSO, x, y, 4, 8, 8, 12, scale);
		part(graphics, state, BodyPart.RIGHT_ARM, x, y, 0, 8, 4, 12, scale);
		part(graphics, state, BodyPart.LEFT_ARM, x, y, 12, 8, 4, 12, scale);
		part(graphics, state, BodyPart.RIGHT_LEG, x, y, 4, 20, 4, 12, scale);
		part(graphics, state, BodyPart.LEFT_LEG, x, y, 8, 20, 4, 12, scale);
	}

	private static void part(GuiGraphicsExtractor graphics, SurvivorState state, BodyPart part, int x, int y, int px, int py, int w, int h, int scale) {
		int x0 = x + px * scale;
		int y0 = y + py * scale;
		int x1 = x0 + w * scale;
		int y1 = y0 + h * scale;
		Wound wound = state.wound(part);
		graphics.fill(x0, y0, x1, y1, OUTLINE);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color(wound));
		if (wound == null) {
			return;
		}

		if (wound.isBandaged()) {
			// A bandage: a white band across the middle, grubby once it's dirty.
			int band = Math.max(2, h * scale / 4);
			int mid = (y0 + y1) / 2;
			graphics.fill(x0 + 1, mid - band / 2, x1 - 1, mid + band / 2, wound.isBandageDirty() ? 0xFF9C8A6A : 0xFFF4F4F4);
		}

		if (wound.infected) {
			graphics.outline(x0, y0, x1 - x0, y1 - y0, 0xFF5FB040);
		}
	}

	static int color(Wound wound) {
		if (wound == null) {
			return HEALTHY;
		}

		if (wound.bleeding && (Util.getMillis() / 400) % 2 == 0) {
			return 0xFF7A0000;
		}

		return switch (wound.type) {
			case SCRATCH -> 0xFFE8C547;
			case LACERATION -> 0xFFE07A2A;
			case BITE -> 0xFFC0201A;
		};
	}
}
