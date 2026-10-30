package io.github.ditsjohann.outbreak.client.menu;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import io.github.ditsjohann.outbreak.character.CharacterData;
import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.character.Names;
import io.github.ditsjohann.outbreak.character.Profession;
import io.github.ditsjohann.outbreak.character.StartPoint;
import io.github.ditsjohann.outbreak.character.Trait;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Making a survivor (PZ's character creation), as a dossier: a name, what they did before, their
 * traits (bought with the profession's points), and where in Groningen they wake up. Comes up when
 * a player joins a world without a survivor, and after a death with permadeath.
 */
public class CharacterCreationScreen extends Screen {
	private static final int ROW = 13;
	private static final int TRAIT_ROW = 11;

	private final Characters.OpenCreationPayload lastLife;
	private final List<Trait> traitList;
	private final List<StartPoint> startPoints;
	private String name;
	private Profession profession = Profession.UNEMPLOYED;
	private final EnumSet<Trait> picked = EnumSet.noneOf(Trait.class);
	private StartPoint start;
	private int traitScroll;
	private Button startButton;

	public CharacterCreationScreen(Characters.OpenCreationPayload lastLife) {
		super(Component.translatable("outbreak.character.title"));
		this.lastLife = lastLife;
		this.name = Names.random(RandomSource.create());
		List<Trait> traits = new ArrayList<>();
		for (Trait trait : Trait.values()) {
			if (trait.isPickable() && trait.isPositive()) {
				traits.add(trait);
			}
		}

		for (Trait trait : Trait.values()) {
			if (trait.isPickable() && !trait.isPositive()) {
				traits.add(trait);
			}
		}

		this.traitList = traits;
		this.startPoints = StartPoint.available(Sandbox.current());
		this.start = this.startPoints.contains(StartPoint.GROTE_MARKT) ? StartPoint.GROTE_MARKT : this.startPoints.getFirst();
	}

	private int left() {
		return Paper.DOSSIER.left(this.width);
	}

	private int top() {
		return Paper.DOSSIER.top(this.height);
	}

	@Override
	protected void init() {
		int left = this.left();
		int top = this.top();

		EditBox nameBox = new EditBox(this.font, left + 12, top + 48, 110, 16, Component.translatable("outbreak.character.name"));
		nameBox.setMaxLength(32);
		nameBox.setValue(this.name);
		nameBox.setResponder(value -> {
			this.name = value;
			this.updateStart();
		});
		this.addRenderableWidget(nameBox);
		this.addRenderableWidget(Button.builder(Component.literal("?"), b -> {
			this.name = Names.random(RandomSource.create());
			nameBox.setValue(this.name);
		}).bounds(left + 124, top + 47, 18, 18).build());

		int right = left + 294;
		this.addRenderableWidget(CycleButton.<StartPoint>builder(p -> Component.translatable(p.translationKey()), this.start)
			.withValues(this.startPoints)
			.create(right, top + 168, 116, 20, Component.translatable("outbreak.character.start"), (button, point) -> this.start = point));
		this.startButton = this.addRenderableWidget(Button.builder(Component.translatable("outbreak.character.begin"), b -> this.begin())
			.bounds(right, top + 192, 116, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("outbreak.character.leave"), b -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE))
			.bounds(right, top + 214, 116, 14).build());
		this.updateStart();
	}

	private void updateStart() {
		if (this.startButton != null) {
			this.startButton.active = CharacterData.isValid(this.name.strip(), this.profession, this.picked);
		}
	}

	private void begin() {
		if (CharacterData.isValid(this.name.strip(), this.profession, this.picked)) {
			ClientPlayNetworking.send(new Characters.CreatePayload(this.name.strip(), this.profession, List.copyOf(this.picked), this.start));
			this.minecraft.gui.setScreen(null);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	// --- Profession list (left) and trait list (middle) ---

	private int professionAt(double mouseX, double mouseY) {
		int x = this.left() + 12;
		int y = this.top() + 80;
		if (mouseX < x || mouseX >= x + 132 || mouseY < y) {
			return -1;
		}

		int index = (int) ((mouseY - y) / ROW);
		return index < Profession.values().length ? index : -1;
	}

	private int traitListTop() {
		return this.top() + 50;
	}

	private int traitListHeight() {
		return 172;
	}

	private int visibleTraits() {
		return this.traitListHeight() / TRAIT_ROW;
	}

	private int traitAt(double mouseX, double mouseY) {
		int x = this.left() + 152;
		int y = this.traitListTop();
		if (mouseX < x || mouseX >= x + 132 || mouseY < y || mouseY >= y + this.visibleTraits() * TRAIT_ROW) {
			return -1;
		}

		int index = this.traitScroll + (int) ((mouseY - y) / TRAIT_ROW);
		return index < this.traitList.size() ? index : -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}

		int profession = this.professionAt(event.x(), event.y());
		if (profession >= 0) {
			this.profession = Profession.values()[profession];
			// A trait that now comes free with the job doesn't need buying.
			this.picked.removeAll(this.profession.traits);
			this.updateStart();
			return true;
		}

		int trait = this.traitAt(event.x(), event.y());
		if (trait >= 0) {
			Trait clicked = this.traitList.get(trait);
			if (!this.picked.remove(clicked) && this.isPickable(clicked)) {
				this.picked.add(clicked);
			}

			this.updateStart();
			return true;
		}

		return false;
	}

	private boolean isPickable(Trait trait) {
		Trait opposite = trait.opposite();
		return !this.profession.traits.contains(trait) && (opposite == null || (!this.picked.contains(opposite) && !this.profession.traits.contains(opposite)));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= this.left() + 152 && mouseX < this.left() + 284) {
			int max = Math.max(0, this.traitList.size() - this.visibleTraits());
			this.traitScroll = Math.max(0, Math.min(max, this.traitScroll - (int) Math.signum(scrollY)));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// --- Drawing ---

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int left = this.left();
		int top = this.top();
		Paper.DOSSIER.draw(graphics, this.width, this.height);
		Paper.heading(graphics, this.font, this.title, left + 12, top + 10);
		if (!this.lastLife.lastName().isEmpty()) {
			graphics.text(this.font, Component.translatable("outbreak.character.in_memory", this.lastLife.lastName(), this.lastLife.lastDays(), this.lastLife.lastKills()),
				left + 12, top + 24, Paper.RED, false);
		} else {
			graphics.text(this.font, Component.translatable("outbreak.character.intro"), left + 12, top + 24, Paper.PENCIL, false);
		}

		graphics.text(this.font, Component.translatable("outbreak.character.name"), left + 12, top + 38, Paper.INK, false);
		this.drawProfessions(graphics, left + 12, top + 70, mouseX, mouseY);
		this.drawTraits(graphics, left + 152, top + 38, mouseX, mouseY);
		this.drawSummary(graphics, left + 294, top + 38, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawProfessions(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("outbreak.character.profession"), x, y, Paper.INK, false);
		int hovered = this.professionAt(mouseX, mouseY);
		Profession[] professions = Profession.values();
		for (int i = 0; i < professions.length; i++) {
			Profession profession = professions[i];
			int rowY = y + 10 + i * ROW;
			boolean selected = profession == this.profession;
			if (selected) {
				graphics.fill(x - 2, rowY - 1, x + 132, rowY + ROW - 1, 0x40261A10);
			} else if (i == hovered) {
				graphics.fill(x - 2, rowY - 1, x + 132, rowY + ROW - 1, 0x20261A10);
			}

			graphics.pose().pushMatrix();
			graphics.pose().translate(x, rowY);
			graphics.pose().scale(0.6875F, 0.6875F);
			graphics.fakeItem(new ItemStack(profession.icon()), 0, 0);
			graphics.pose().popMatrix();
			graphics.text(this.font, Component.translatable(profession.translationKey()), x + 14, rowY + 2, selected ? Paper.RED : Paper.INK, false);
			String points = (profession.points > 0 ? "+" : "") + profession.points;
			graphics.text(this.font, points, x + 130 - this.font.width(points), rowY + 2, Paper.PENCIL, false);
		}
	}

	private void drawTraits(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("outbreak.character.traits"), x, y, Paper.INK, false);
		int listTop = this.traitListTop();
		int hovered = this.traitAt(mouseX, mouseY);
		int visible = this.visibleTraits();
		for (int row = 0; row < visible && this.traitScroll + row < this.traitList.size(); row++) {
			int index = this.traitScroll + row;
			Trait trait = this.traitList.get(index);
			int rowY = listTop + row * TRAIT_ROW;
			boolean free = this.profession.traits.contains(trait);
			boolean selected = this.picked.contains(trait) || free;
			boolean pickable = selected || this.isPickable(trait);
			if (selected) {
				graphics.fill(x - 2, rowY - 1, x + 132, rowY + TRAIT_ROW - 1, 0x40261A10);
			} else if (index == hovered) {
				graphics.fill(x - 2, rowY - 1, x + 132, rowY + TRAIT_ROW - 1, 0x20261A10);
			}

			int color = !pickable ? 0x80423629 : trait.isPositive() ? Paper.INK : Paper.RED;
			MutableComponent label = Component.literal(selected ? "x " : "  ").append(Component.translatable(trait.translationKey()));
			graphics.text(this.font, label, x, rowY + 1, color, false);
			String cost = free ? "free" : trait.isPositive() ? "-" + trait.cost : "+" + (-trait.cost);
			graphics.text(this.font, cost, x + 130 - this.font.width(cost), rowY + 1, trait.isPositive() ? Paper.PENCIL : Paper.GREEN, false);
		}

		// Scroll position.
		int max = this.traitList.size() - visible;
		if (max > 0) {
			int barHeight = this.traitListHeight() * visible / this.traitList.size();
			int barY = listTop + (this.traitListHeight() - barHeight) * this.traitScroll / max;
			graphics.fill(x + 134, listTop, x + 135, listTop + this.traitListHeight(), 0x30261A10);
			graphics.fill(x + 133, barY, x + 136, barY + barHeight, Paper.PENCIL);
		}

		if (hovered >= 0) {
			Trait trait = this.traitList.get(hovered);
			graphics.setTooltipForNextFrame(this.font.split(Component.translatable(trait.descriptionKey()), 160), mouseX, mouseY);
		}
	}

	private void drawSummary(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable(this.profession.translationKey()), x, y, Paper.INK, false);
		int textY = Paper.wrapped(graphics, this.font, Component.translatable(this.profession.descriptionKey()), x, y + 12, 116, Paper.PENCIL);
		if (!this.profession.skills.isEmpty()) {
			String skills = this.profession.skills.entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.map(e -> Component.translatable(e.getKey().translationKey()).getString() + " " + e.getValue())
				.collect(Collectors.joining(", "));
			textY = Paper.wrapped(graphics, this.font, Component.literal(skills), x, textY + 2, 116, Paper.INK);
		}

		for (Trait trait : this.profession.traits) {
			textY = Paper.wrapped(graphics, this.font, Component.translatable("outbreak.character.free_trait", Component.translatable(trait.translationKey())), x, textY, 116, Paper.GREEN);
		}

		// What they had on them.
		List<ItemStack> kit = new ArrayList<>(Profession.basics());
		kit.addAll(this.profession.kit());
		int itemX = x;
		int itemY = textY + 3;
		for (ItemStack stack : kit) {
			if (itemX + 16 > x + 118) {
				itemX = x;
				itemY += 17;
			}

			graphics.fakeItem(stack, itemX, itemY);
			graphics.itemDecorations(this.font, stack, itemX, itemY);
			if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
				graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
			}

			itemX += 17;
		}

		int points = CharacterData.pointsLeft(this.profession, this.picked);
		graphics.text(this.font, Component.translatable("outbreak.character.points", points), x, itemY + 20, points < 0 ? Paper.RED : Paper.GREEN, false);
	}
}
