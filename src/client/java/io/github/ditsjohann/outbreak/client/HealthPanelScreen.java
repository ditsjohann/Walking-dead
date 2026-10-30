package io.github.ditsjohann.outbreak.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.inventory.CarryWeight;
import io.github.ditsjohann.outbreak.survivor.Moodle;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;
import io.github.ditsjohann.outbreak.survivor.body.WoundType;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** The health panel (H): a big body doll, every wound in words, and how you feel. Doesn't pause the game. */
public class HealthPanelScreen extends Screen {
	private static final Identifier PANEL = Identifier.fromNamespaceAndPath(Outbreak.MOD_ID, "textures/gui/health_panel.png");
	private static final int PANEL_WIDTH = 330;
	private static final int PANEL_HEIGHT = 210;
	private static final int TEXT_WIDTH = 216;
	private static final int DOLL_SCALE = 4;

	private static final int BANDAGED = 0xFF1E4650;
	private static final int DIRTY = 0xFF7A4A10;
	private static final int INFECTED = 0xFF2E5A1E;

	public HealthPanelScreen() {
		super(Component.translatable("outbreak.health.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (OutbreakClient.HEALTH_KEY.matches(event)) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}

		SurvivorState state = Survivors.get(player);
		int left = (this.width - PANEL_WIDTH) / 2;
		int top = (this.height - PANEL_HEIGHT) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);

		graphics.text(this.font, this.title, left + 14, top + 12, PaperStyle.INK, false);
		graphics.fill(left + 14, top + 22, left + 14 + this.font.width(this.title) + 4, top + 23, PaperStyle.PENCIL);
		BodyDoll.draw(graphics, state, left + 18, top + 36, DOLL_SCALE);

		int textX = left + 100;
		int y = top + 12;
		int health = Math.round(player.getHealth() / player.getMaxHealth() * 100);
		graphics.text(this.font, Component.translatable("outbreak.health.overall", health), textX, y, health <= 30 ? PaperStyle.MARKER_RED : PaperStyle.INK, false);
		y += 11;
		graphics.text(this.font, Component.translatable("outbreak.health.carrying",
			String.format(Locale.ROOT, "%.1f", CarryWeight.total(player)), CarryWeight.capacityLabel(player)), textX, y, PaperStyle.PENCIL, false);
		y += 15;

		if (state.wounds.isEmpty()) {
			graphics.text(this.font, Component.translatable("outbreak.health.no_wounds"), textX, y, PaperStyle.PENCIL, false);
			y += 11;
		}

		for (BodyPart part : BodyPart.values()) {
			Wound wound = state.wound(part);
			if (wound != null) {
				for (FormattedCharSequence line : this.font.split(describe(part, wound), TEXT_WIDTH)) {
					graphics.text(this.font, line, textX, y, PaperStyle.INK, false);
					y += 10;
				}

				y += 1;
			}
		}

		y += 7;
		graphics.text(this.font, Component.translatable("outbreak.health.status"), textX, y, PaperStyle.INK, false);
		graphics.fill(textX, y + 10, textX + this.font.width(Component.translatable("outbreak.health.status")) + 4, y + 11, PaperStyle.PENCIL);
		y += 16;
		// Two columns, so a really bad day still fits on the page.
		boolean any = false;
		int column = 0;
		for (Moodle moodle : Moodle.values()) {
			int level = moodle.level(player, state);
			if (level > 0) {
				any = true;
				int x = textX + column * 108;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, OutbreakHud.moodleSprite(moodle), x, y - 4, 16, 16);
				graphics.text(this.font, Component.translatable(moodle.translationKey(level)), x + 19, y, levelColor(level), false);
				column++;
				if (column == 2) {
					column = 0;
					y += 16;
				}
			}
		}

		if (!any) {
			graphics.text(this.font, Component.translatable("outbreak.health.fine"), textX, y, PaperStyle.PENCIL, false);
		}
	}

	private static Component describe(BodyPart part, Wound wound) {
		List<Component> details = new ArrayList<>();
		if (wound.bleeding) {
			details.add(Component.translatable("outbreak.health.bleeding").withColor(PaperStyle.MARKER_RED));
		}

		details.add(Component.translatable(wound.bandage.translationKey()).withColor(wound.isBandaged() ? BANDAGED : PaperStyle.PENCIL));
		if (wound.isBandageDirty()) {
			details.add(Component.translatable("outbreak.health.dirty").withColor(DIRTY));
		}

		if (wound.infected) {
			details.add(Component.translatable("outbreak.health.infected").withColor(INFECTED));
		}

		if (!wound.bleeding) {
			details.add(Component.translatable("outbreak.health.healing", String.format(Locale.ROOT, "%.0f", wound.healProgress() * 100)).withColor(PaperStyle.PENCIL));
		}

		MutableComponent line = Component.translatable(part.translationKey()).append(": ")
			.append(Component.translatable(wound.type.translationKey()).withColor(wound.type == WoundType.SCRATCH ? PaperStyle.INK : PaperStyle.MARKER_RED));
		for (Component detail : details) {
			line.append(Component.literal(" · ").withColor(PaperStyle.PENCIL)).append(detail);
		}

		return line;
	}

	private static int levelColor(int level) {
		return switch (level) {
			case 1 -> PaperStyle.PENCIL;
			case 2 -> 0xFF7A4A10;
			default -> PaperStyle.MARKER_RED;
		};
	}
}
