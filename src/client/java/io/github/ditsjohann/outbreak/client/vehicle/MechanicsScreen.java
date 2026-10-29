package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.Locale;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.PaperStyle;
import io.github.ditsjohann.outbreak.vehicle.MechanicsMenu;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The mechanics page: a pencil sketch of the vehicle seen from above with its tires and battery in
 * slots, its state written next to it, and repairs you can click.
 */
public class MechanicsScreen extends AbstractContainerScreen<MechanicsMenu> {
	private static final int TEXT_X = 100;
	private static final int REPAIR_ENGINE_Y = 84;
	private static final int FIX_WINDOWS_Y = 96;

	private final Identifier texture;

	public MechanicsScreen(MechanicsMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, MechanicsMenu.SCREEN_WIDTH, MechanicsMenu.SCREEN_HEIGHT);
		Vehicle vehicle = menu.vehicle();
		boolean car = vehicle == null || vehicle.vehicleType().isCar();
		this.texture = Outbreak.id("textures/gui/container/mechanics_" + (car ? "car" : vehicle.vehicleType().id()) + ".png");
		this.inventoryLabelY = MechanicsMenu.POCKETS_TOP - 11;
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
		Vehicle vehicle = this.menu.vehicle();
		if (vehicle == null) {
			return;
		}

		VehicleType type = vehicle.vehicleType();
		int y = 20;
		if (type.hasEngine) {
			y = this.line(graphics, Component.translatable("outbreak.mechanics.engine", vehicle.engine()), vehicle.engine() < 30, y);
			y = this.line(graphics, Component.translatable("outbreak.mechanics.fuel",
				String.format(Locale.ROOT, "%.0f", vehicle.fuel()), String.format(Locale.ROOT, "%.0f", type.tankSize)), vehicle.fuel() < 1.0F, y);
			float charge = vehicle.batteryCharge();
			y = this.line(graphics, charge < 0.0F ? Component.translatable("outbreak.mechanics.no_battery")
				: Component.translatable("outbreak.mechanics.battery", Math.round(charge * 100)), charge < 0.1F, y);
		}

		if (type.hasWindows) {
			y = this.line(graphics, Component.translatable("outbreak.mechanics.windows", vehicle.windows()), vehicle.windows() <= 0, y);
		}

		y = this.line(graphics, Component.translatable("outbreak.mechanics.body", vehicle.body()), vehicle.body() < 30, y);
		if (vehicle.isWreck()) {
			this.line(graphics, Component.translatable("outbreak.mechanics.wreck"), true, y);
		} else if (vehicle.isKeyInside()) {
			this.line(graphics, Component.translatable("outbreak.mechanics.key_inside"), false, y);
		}

		if (type.hasEngine) {
			this.link(graphics, Component.translatable("outbreak.mechanics.repair_engine"), REPAIR_ENGINE_Y, mouseX, mouseY);
		}

		if (type.hasWindows) {
			this.link(graphics, Component.translatable("outbreak.mechanics.fix_windows"), FIX_WINDOWS_Y, mouseX, mouseY);
		}
	}

	private int line(GuiGraphicsExtractor graphics, Component text, boolean bad, int y) {
		graphics.text(this.font, text, TEXT_X, y, bad ? PaperStyle.MARKER_RED : PaperStyle.PENCIL, false);
		return y + 10;
	}

	/** A repair written like a to-do item; underlined in red when the mouse is on it. */
	private void link(GuiGraphicsExtractor graphics, Component text, int y, int mouseX, int mouseY) {
		boolean hover = this.overLink(text, y, mouseX, mouseY);
		int width = this.font.width(text);
		graphics.text(this.font, text, TEXT_X, y, hover ? PaperStyle.MARKER_RED : PaperStyle.INK, false);
		graphics.fill(TEXT_X, y + 9, TEXT_X + width, y + 10, hover ? PaperStyle.MARKER_RED : PaperStyle.PENCIL);
	}

	private boolean overLink(Component text, int y, double mouseX, double mouseY) {
		double x = mouseX - this.leftPos;
		double localY = mouseY - this.topPos;
		return x >= TEXT_X && x < TEXT_X + this.font.width(text) && localY >= y - 1 && localY < y + 10;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Vehicle vehicle = this.menu.vehicle();
		if (vehicle != null && event.button() == 0) {
			int button = -1;
			if (vehicle.vehicleType().hasEngine && this.overLink(Component.translatable("outbreak.mechanics.repair_engine"), REPAIR_ENGINE_Y, event.x(), event.y())) {
				button = MechanicsMenu.BUTTON_REPAIR_ENGINE;
			} else if (vehicle.vehicleType().hasWindows && this.overLink(Component.translatable("outbreak.mechanics.fix_windows"), FIX_WINDOWS_Y, event.x(), event.y())) {
				button = MechanicsMenu.BUTTON_FIX_WINDOWS;
			}

			if (button >= 0 && this.minecraft.gameMode != null) {
				this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		Vehicle vehicle = this.menu.vehicle();
		if (vehicle != null && this.hoveredSlot instanceof MechanicsMenu.PartSlot slot && !slot.hasItem() && this.menu.getCarried().isEmpty()) {
			Component name = slot.isBattery() ? Component.translatable("item.outbreak.car_battery")
				: Component.translatable(vehicle.tireItem().getDescriptionId());
			Component missing = slot.missingTools();
			graphics.setTooltipForNextFrame(missing == null ? name : name.copy().append(" - ").append(missing), mouseX, mouseY);
		}
	}
}
