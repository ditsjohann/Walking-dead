package io.github.ditsjohann.outbreak.client.menu;

import java.util.function.Consumer;
import java.util.function.Function;

import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * PZ's sandbox settings: a preset, and every rule of it to change by hand (which makes it "custom").
 * Used when setting up a new survivor, and by operators in game ({@code /outbreak sandbox}).
 */
public class SandboxScreen extends Screen {
	private final @Nullable Screen parent;
	private final Consumer<SandboxSettings> onDone;
	private SandboxSettings settings;

	public SandboxScreen(@Nullable Screen parent, SandboxSettings settings, Consumer<SandboxSettings> onDone) {
		super(Component.translatable("outbreak.sandbox.title"));
		this.parent = parent;
		this.settings = settings;
		this.onDone = onDone;
	}

	@Override
	protected void init() {
		Paper page = Paper.NEW_SURVIVOR;
		int left = page.left(this.width) + 14;
		int top = page.top(this.height);
		int column = 186;

		this.addRenderableWidget(CycleButton.<SandboxSettings.Preset>builder(p -> Component.translatable(p.translationKey()), this.settings.preset())
			.withValues(SandboxSettings.Preset.values())
			.create(left, top + 38, 372, 20, Component.translatable("outbreak.sandbox.preset"), (button, preset) -> {
				this.settings = this.settings.withPreset(preset);
				this.rebuildWidgets();
			}));

		int y = top + 66;
		this.addRenderableWidget(this.cycle("walker_speed", SandboxSettings.WalkerSpeed.values(), this.settings.walkerSpeed(), SandboxSettings.WalkerSpeed::translationKey, left, y));
		this.addRenderableWidget(this.cycle("population", SandboxSettings.Population.values(), this.settings.population(), SandboxSettings.Population::translationKey, left + column, y));
		y += 24;
		this.addRenderableWidget(this.cycle("herd_size", SandboxSettings.HerdSize.values(), this.settings.herdSize(), SandboxSettings.HerdSize::translationKey, left, y));
		this.addRenderableWidget(this.cycle("infection", SandboxSettings.Infection.values(), this.settings.infection(), SandboxSettings.Infection::translationKey, left + column, y));
		y += 24;
		this.addRenderableWidget(this.cycle("loot", SandboxSettings.Loot.values(), this.settings.loot(), SandboxSettings.Loot::translationKey, left, y));
		this.addRenderableWidget(this.toggle("permadeath", this.settings.permadeath(), left + column, y));
		y += 24;
		this.addRenderableWidget(this.toggle("vanilla_monsters", this.settings.vanillaMonsters(), left, y));

		if (this.settings.map() == SandboxSettings.MapKind.GRONINGEN) {
			EditBox bbox = new EditBox(this.font, left, y + 38, 372, 18, Component.translatable("outbreak.sandbox.bbox"));
			bbox.setMaxLength(64);
			bbox.setValue(this.settings.bbox());
			bbox.setResponder(value -> this.settings = this.settings.withMap(this.settings.map(), value));
			this.addRenderableWidget(bbox);
		}

		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.close())
			.bounds(left, top + page.height() - 30, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
			this.onDone.accept(this.settings);
			this.close();
		}).bounds(left + 372 - 120, top + page.height() - 30, 120, 20).build());
	}

	private <E extends Enum<E> & StringRepresentable> CycleButton<E> cycle(String key, E[] values, E current, Function<E, String> translationKey, int x, int y) {
		return CycleButton.<E>builder(value -> Component.translatable(translationKey.apply(value)), current)
			.withValues(values)
			.create(x, y, 180, 20, Component.translatable("outbreak.sandbox." + key), (button, value) -> this.change(key, value.getSerializedName()));
	}

	private CycleButton<Boolean> toggle(String key, boolean current, int x, int y) {
		return CycleButton.onOffBuilder(current)
			.create(x, y, 180, 20, Component.translatable("outbreak.sandbox." + key), (button, value) -> this.change(key, value.toString()));
	}

	/** One rule changed by hand: the preset turns to custom, and its button has to say so. */
	private void change(String key, String value) {
		boolean wasCustom = this.settings.preset() == SandboxSettings.Preset.CUSTOM;
		this.settings = this.settings.with(key, value);
		if (!wasCustom) {
			this.rebuildWidgets();
		}
	}

	private void close() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void onClose() {
		this.close();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Paper page = Paper.NEW_SURVIVOR;
		int left = page.left(this.width) + 14;
		int top = page.top(this.height);
		page.draw(graphics, this.width, this.height);
		Paper.heading(graphics, this.font, this.title, left, top + 12);
		graphics.text(this.font, Component.translatable("outbreak.sandbox.intro"), left, top + 26, Paper.PENCIL, false);
		if (this.settings.map() == SandboxSettings.MapKind.GRONINGEN) {
			graphics.text(this.font, Component.translatable("outbreak.sandbox.bbox"), left, top + 66 + 3 * 24 + 26, Paper.INK, false);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
}
