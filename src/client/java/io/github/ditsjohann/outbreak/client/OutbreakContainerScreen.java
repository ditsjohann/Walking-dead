package io.github.ditsjohann.outbreak.client;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.inventory.ContainerKind;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** A container screen with its own look per {@link ContainerKind}: textures/gui/container/&lt;kind&gt;.png. */
public class OutbreakContainerScreen extends AbstractContainerScreen<OutbreakMenu> {
	private final ContainerKind kind;
	private final Identifier texture;

	public OutbreakContainerScreen(OutbreakMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, ContainerKind.SCREEN_WIDTH, menu.kind().screenHeight());
		this.kind = menu.kind();
		this.texture = Outbreak.id("textures/gui/container/" + this.kind.id() + ".png");
		this.inventoryLabelY = this.kind.pocketsTop() - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, this.leftPos, this.topPos, 0.0F, 0.0F,
			this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, PaperStyle.INK, false);
		graphics.text(this.font, Component.translatable("outbreak.screen.pockets"), this.inventoryLabelX, this.inventoryLabelY, PaperStyle.INK, false);
	}
}
