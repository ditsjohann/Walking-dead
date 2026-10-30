package io.github.ditsjohann.outbreak.client.menu;

import java.util.Locale;

import io.github.ditsjohann.outbreak.character.CharacterData;
import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.character.Perks;
import io.github.ditsjohann.outbreak.character.Skill;
import io.github.ditsjohann.outbreak.character.Trait;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The character sheet (K): who you are, your traits, and how far along each skill is. Doesn't pause the game. */
public class CharacterSheetScreen extends Screen {
	private static final int BOX = 8;

	private final KeyMapping key;

	public CharacterSheetScreen(KeyMapping key) {
		super(Component.translatable("outbreak.sheet.title"));
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.key.matches(event)) {
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

		Paper page = Paper.CHARACTER_SHEET;
		int x = page.left(this.width) + 14;
		int top = page.top(this.height);
		page.draw(graphics, this.width, this.height);
		CharacterData data = Characters.get(player);
		if (!data.created) {
			graphics.text(this.font, Component.translatable("outbreak.sheet.nobody"), x, top + 14, Paper.PENCIL, false);
			return;
		}

		Paper.heading(graphics, this.font, Component.literal(data.name), x, top + 12);
		long day = player.level().getOverworldClockTime() / 24000L + 1;
		graphics.text(this.font, Component.translatable("outbreak.sheet.summary", Component.translatable(data.profession.translationKey()), day,
			Characters.daysSurvived(player, data), data.kills), x, top + 26, Paper.PENCIL, false);

		// Traits, good in ink and bad in red.
		MutableComponent traits = Component.empty();
		boolean first = true;
		for (Trait trait : data.traits) {
			if (!first) {
				traits.append(Component.literal(", ").withColor(Paper.PENCIL));
			}

			traits.append(Component.translatable(trait.translationKey()).withColor(trait.isPositive() || trait.cost == 0 ? Paper.INK : Paper.RED));
			first = false;
		}

		int y = Paper.wrapped(graphics, this.font, data.traits.isEmpty() ? Component.translatable("outbreak.sheet.no_traits") : traits, x, top + 40, 272, Paper.INK);

		y += 4;
		graphics.text(this.font, Component.translatable("outbreak.sheet.skills"), x, y, Paper.INK, false);
		graphics.fill(x, y + 10, x + this.font.width(Component.translatable("outbreak.sheet.skills")) + 4, y + 11, Paper.PENCIL);
		y += 15;
		for (Skill skill : Skill.values()) {
			float xp = data.xp(skill);
			int level = Skill.level(xp);
			graphics.text(this.font, Component.translatable(skill.translationKey()), x, y, Paper.INK, false);
			int boxX = x + 84;
			for (int i = 0; i < Skill.MAX_LEVEL; i++) {
				int bx = boxX + i * (BOX + 2);
				graphics.outline(bx, y, BOX, BOX, Paper.PENCIL);
				if (i < level) {
					graphics.fill(bx + 1, y + 1, bx + BOX - 1, y + BOX - 1, Paper.INK);
				} else if (i == level) {
					// The level being worked on fills up from the left.
					int fill = Math.round((BOX - 2) * Skill.progress(xp));
					graphics.fill(bx + 1, y + 1, bx + 1 + fill, y + BOX - 1, Paper.RED);
				}
			}

			graphics.text(this.font, String.valueOf(level), boxX + Skill.MAX_LEVEL * (BOX + 2) + 4, y, level > 0 ? Paper.INK : Paper.PENCIL, false);
			y += 12;
		}

		graphics.text(this.font, Component.translatable("outbreak.sheet.capacity", String.format(Locale.ROOT, "%.1f", Perks.capacity(player))), x, top + page.height() - 18, Paper.PENCIL, false);
	}
}
