package io.github.ditsjohann.outbreak.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.ditsjohann.outbreak.inventory.CarryWeight;
import io.github.ditsjohann.outbreak.survivor.Moodle;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The health panel (H): a big body doll, every wound in words, and how you feel. Doesn't pause the game. */
public class HealthPanelScreen extends Screen {
	private static final int DOLL_SCALE = 4;

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
		int dollWidth = BodyDoll.WIDTH * DOLL_SCALE;
		int panelWidth = dollWidth + 24 + 220;
		int left = (this.width - panelWidth) / 2;
		int top = Math.max(20, (this.height - BodyDoll.HEIGHT * DOLL_SCALE) / 2 - 10);

		graphics.centeredText(this.font, this.title, this.width / 2, top - 16, 0xFFFFFFFF);
		BodyDoll.draw(graphics, state, left, top, DOLL_SCALE);

		int textX = left + dollWidth + 24;
		int y = top;
		int health = Math.round(player.getHealth() / player.getMaxHealth() * 100);
		graphics.text(this.font, Component.translatable("outbreak.health.overall", health), textX, y, 0xFFFFFFFF);
		y += 12;
		graphics.text(this.font, Component.translatable("outbreak.health.carrying",
			String.format(Locale.ROOT, "%.1f", CarryWeight.total(player)), String.format(Locale.ROOT, "%.0f", CarryWeight.CAPACITY)), textX, y, 0xFFC8C8C8);
		y += 14;

		if (state.wounds.isEmpty()) {
			graphics.text(this.font, Component.translatable("outbreak.health.no_wounds").withStyle(ChatFormatting.GRAY), textX, y, 0xFFFFFFFF);
			y += 12;
		}

		for (BodyPart part : BodyPart.values()) {
			Wound wound = state.wound(part);
			if (wound != null) {
				graphics.text(this.font, describe(part, wound), textX, y, 0xFFFFFFFF);
				y += 12;
			}
		}

		y += 8;
		graphics.text(this.font, Component.translatable("outbreak.health.status").withStyle(ChatFormatting.UNDERLINE), textX, y, 0xFFFFFFFF);
		y += 14;
		boolean any = false;
		for (Moodle moodle : Moodle.values()) {
			int level = moodle.level(player, state);
			if (level > 0) {
				any = true;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SurvivorHud.sprite(moodle), textX, y - 4, 16, 16);
				graphics.text(this.font, Component.translatable(moodle.translationKey(level)), textX + 20, y, levelColor(level));
				y += 16;
			}
		}

		if (!any) {
			graphics.text(this.font, Component.translatable("outbreak.health.fine").withStyle(ChatFormatting.GRAY), textX, y, 0xFFFFFFFF);
		}
	}

	private static Component describe(BodyPart part, Wound wound) {
		List<Component> details = new ArrayList<>();
		if (wound.bleeding) {
			details.add(Component.translatable("outbreak.health.bleeding").withStyle(ChatFormatting.DARK_RED));
		}

		details.add(Component.translatable(wound.bandage.translationKey()).withStyle(wound.isBandaged() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		if (wound.isBandageDirty()) {
			details.add(Component.translatable("outbreak.health.dirty").withStyle(ChatFormatting.GOLD));
		}

		if (wound.infected) {
			details.add(Component.translatable("outbreak.health.infected").withStyle(ChatFormatting.GREEN));
		}

		if (!wound.bleeding) {
			details.add(Component.translatable("outbreak.health.healing", String.format(Locale.ROOT, "%.0f", wound.healProgress() * 100)).withStyle(ChatFormatting.GRAY));
		}

		MutableComponent line = Component.translatable(part.translationKey()).append(": ")
			.append(Component.translatable(wound.type.translationKey()).withColor(BodyDoll.color(wound) | 0xFF000000));
		for (Component detail : details) {
			line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(detail);
		}

		return line;
	}

	private static int levelColor(int level) {
		return switch (level) {
			case 1 -> 0xFFE8D27A;
			case 2 -> 0xFFF0A050;
			case 3 -> 0xFFF06050;
			default -> 0xFFFF3030;
		};
	}
}
