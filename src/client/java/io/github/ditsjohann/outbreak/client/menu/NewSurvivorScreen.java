package io.github.ditsjohann.outbreak.client.menu;

import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.sandbox.SandboxSettings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * "New survivor", step one (PZ's scenario screen): when the story starts, how hard it is, and what
 * map it's played on. Then the world is picked (or made); whichever world opens next and hasn't been
 * played yet takes these rules, and the character screen comes up as it loads.
 */
public class NewSurvivorScreen extends Screen {
	private final Screen parent;
	private SandboxSettings settings;

	public NewSurvivorScreen(Screen parent) {
		super(Component.translatable("outbreak.new_survivor.title"));
		this.parent = parent;
		this.settings = SandboxSettings.DEFAULT;
	}

	@Override
	protected void init() {
		Paper page = Paper.NEW_SURVIVOR;
		int left = page.left(this.width);
		int top = page.top(this.height);

		int y = top + 50;
		for (SandboxSettings.Scenario scenario : SandboxSettings.Scenario.values()) {
			Component label = Component.translatable(scenario.translationKey());
			Button button = Button.builder(scenario == this.settings.scenario() ? Component.literal("» ").append(label) : label, b -> {
				this.settings = this.settings.withScenario(scenario);
				this.rebuildWidgets();
			}).bounds(left + 14, y, 172, 20).build();
			this.addRenderableWidget(button);
			y += 22;
		}

		int right = left + 206;
		this.addRenderableWidget(CycleButton.<SandboxSettings.Preset>builder(p -> Component.translatable(p.translationKey()), this.settings.preset())
			.withValues(SandboxSettings.Preset.values())
			.create(right, top + 50, 180, 20, Component.translatable("outbreak.sandbox.preset"), (button, preset) -> {
				this.settings = this.settings.withPreset(preset);
			}));
		this.addRenderableWidget(CycleButton.<SandboxSettings.MapKind>builder(m -> Component.translatable(m.translationKey()), this.settings.map())
			.withValues(SandboxSettings.MapKind.values())
			.create(right, top + 124, 180, 20, Component.translatable("outbreak.sandbox.map"), (button, map) -> this.settings = this.settings.withMap(map, this.settings.bbox())));
		this.addRenderableWidget(Button.builder(Component.translatable("outbreak.new_survivor.customise"), b ->
				this.minecraft.gui.setScreen(new SandboxScreen(this, this.settings, changed -> this.settings = changed)))
			.bounds(right, top + 148, 180, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose())
			.bounds(left + 14, top + page.height() - 30, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("outbreak.new_survivor.pick_world"), b -> {
			Sandbox.setPending(this.settings);
			this.minecraft.gui.setScreen(new SelectWorldScreen(this.parent));
		}).bounds(left + page.width() - 174, top + page.height() - 30, 160, 20).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Paper page = Paper.NEW_SURVIVOR;
		int left = page.left(this.width);
		int top = page.top(this.height);
		page.draw(graphics, this.width, this.height);
		Paper.heading(graphics, this.font, this.title, left + 14, top + 12);
		graphics.text(this.font, Component.translatable("outbreak.new_survivor.intro"), left + 14, top + 26, Paper.PENCIL, false);

		graphics.text(this.font, Component.translatable("outbreak.new_survivor.when"), left + 14, top + 40, Paper.INK, false);
		SandboxSettings.Scenario scenario = this.settings.scenario();
		int y = top + 50 + 3 * 22 + 2;
		graphics.text(this.font, Component.translatable("outbreak.new_survivor.day", scenario.startDay), left + 14, y, Paper.RED, false);
		Paper.wrapped(graphics, this.font, Component.translatable(scenario.translationKey() + ".desc"), left + 14, y + 11, 172, Paper.PENCIL);

		int right = left + 206;
		graphics.text(this.font, Component.translatable("outbreak.new_survivor.rules"), right, top + 40, Paper.INK, false);
		Paper.wrapped(graphics, this.font, Component.translatable(this.settings.preset().translationKey() + ".desc"), right, top + 74, 180, Paper.PENCIL);
		Component map = this.settings.map() == SandboxSettings.MapKind.GRONINGEN
			? Component.translatable("outbreak.new_survivor.map_groningen") : Component.translatable("outbreak.new_survivor.map_any");
		Paper.wrapped(graphics, this.font, map, right, top + 172, 180, Paper.PENCIL);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
}
