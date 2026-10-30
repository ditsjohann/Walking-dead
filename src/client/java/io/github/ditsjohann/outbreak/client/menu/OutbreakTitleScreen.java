package io.github.ditsjohann.outbreak.client.menu;

import java.util.Comparator;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.mixin.ScreenAccessor;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Our title screen, in place of Minecraft's: Groningen burning behind the logo, and the way into a
 * new survivor, the last world, a server, or the options. It is a TitleScreen (the game and other
 * mods check for one) but draws nothing of Minecraft's.
 */
public class OutbreakTitleScreen extends TitleScreen {
	private static final Identifier LOGO = Outbreak.id("textures/gui/title/logo.png");
	private static final int LOGO_WIDTH = 291;
	private static final int LOGO_HEIGHT = 89;
	private static final int BUTTON_WIDTH = 180;

	private @Nullable LevelSummary lastWorld;
	private @Nullable Button continueButton;

	public OutbreakTitleScreen() {
		super(false, null);
	}

	@Override
	public Component getTitle() {
		return Component.translatable("outbreak.title");
	}

	@Override
	protected void init() {
		this.minecraft.gameRenderer.panorama().startSpin();
		// Back at the title: whatever was picked on the new survivor screens is off.
		Sandbox.setPending(null);
		int x = this.width / 2 - BUTTON_WIDTH / 2;
		int y = this.logoBottom() + 8;
		int step = 23;

		this.continueButton = this.addRenderableWidget(Button.builder(Component.translatable("outbreak.title.continue"), b -> this.continueLastWorld())
			.bounds(x, y, BUTTON_WIDTH, 20).build());
		this.continueButton.active = false;
		this.addRenderableWidget(Button.builder(Component.translatable("outbreak.title.new_survivor"), b -> this.minecraft.gui.setScreen(new NewSurvivorScreen(this)))
			.bounds(x, y + step, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("outbreak.title.load_world"), b -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)))
			.bounds(x, y + step * 2, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("outbreak.title.join_server"), b -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this)))
			.bounds(x, y + step * 3, BUTTON_WIDTH, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("menu.options"), b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options)))
			.bounds(x, y + step * 4, BUTTON_WIDTH / 2 - 2, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> this.minecraft.stop())
			.bounds(x + BUTTON_WIDTH / 2 + 2, y + step * 4, BUTTON_WIDTH / 2 - 2, 20).build());

		this.findLastWorld();
	}

	/** The logo is drawn as big as fits nicely: twice its size on a big screen, smaller on a small one. */
	private float logoScale() {
		float byWidth = (this.width * 0.7F) / LOGO_WIDTH;
		float byHeight = (this.height * 0.3F) / LOGO_HEIGHT;
		return Math.max(0.5F, Math.min(2.0F, Math.min(byWidth, byHeight)));
	}

	private int logoTop() {
		return Math.max(6, this.height / 2 - 120);
	}

	private int logoBottom() {
		return this.logoTop() + Math.round(LOGO_HEIGHT * this.logoScale());
	}

	private void findLastWorld() {
		try {
			var candidates = this.minecraft.getLevelSource().findLevelCandidates();
			this.minecraft.getLevelSource().loadLevelSummaries(candidates).thenAcceptAsync(list -> {
				this.lastWorld = list.stream()
					.filter(summary -> !summary.isDisabled() && summary.primaryActionActive())
					.max(Comparator.comparingLong(LevelSummary::getLastPlayed))
					.orElse(null);
				if (this.lastWorld != null && this.continueButton != null) {
					this.continueButton.setMessage(Component.translatable("outbreak.title.continue_world", this.lastWorld.getLevelName()));
					this.continueButton.active = true;
				}
			}, this.minecraft);
		} catch (LevelStorageException e) {
			Outbreak.LOGGER.warn("Couldn't list the worlds", e);
		}
	}

	private void continueLastWorld() {
		if (this.lastWorld != null) {
			this.minecraft.createWorldOpenFlows().openWorld(this.lastWorld.getLevelId(), () -> this.minecraft.gui.setScreen(this));
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractPanorama(graphics, partialTick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Our widgets only: TitleScreen's own drawing (the Minecraft logo, the splash) is skipped.
		for (Renderable renderable : ((ScreenAccessor) this).outbreak$renderables()) {
			renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		float scale = this.logoScale();
		int width = Math.round(LOGO_WIDTH * scale);
		int height = Math.round(LOGO_HEIGHT * scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, (this.width - width) / 2, this.logoTop(), 0.0F, 0.0F, width, height, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);

		String version = FabricLoader.getInstance().getModContainer(Outbreak.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
		graphics.text(this.font, Component.translatable("outbreak.title.version", version, SharedConstants.getCurrentVersion().name()), 3, this.height - 11, 0xFF8C857A);
		Component credit = Component.translatable("outbreak.title.osm");
		graphics.text(this.font, credit, this.width - this.font.width(credit) - 3, this.height - 11, 0xFF8C857A);
	}
}
