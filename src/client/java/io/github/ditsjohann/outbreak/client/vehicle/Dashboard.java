package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.Locale;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.PaperStyle;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * While you're in a vehicle: a note in the bottom right with your speed, the fuel gauge, the engine
 * and battery, and a little diagram of the tires.
 */
public final class Dashboard {
	private static final Identifier PANEL = Outbreak.id("hud/dashboard");
	private static final int WIDTH = 128;
	private static final int HEIGHT = 44;
	/** km/h per block per tick. */
	private static final float KMH = 72.0F;

	private static float shownSpeed;

	private Dashboard() {
	}

	public static void extract(GuiGraphicsExtractor graphics, Font font, LocalPlayer player) {
		if (!(player.getVehicle() instanceof Vehicle vehicle)) {
			shownSpeed = 0.0F;
			return;
		}

		VehicleType type = vehicle.vehicleType();
		int left = graphics.guiWidth() - WIDTH - 3;
		int top = graphics.guiHeight() - HEIGHT - 3;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, left, top, WIDTH, HEIGHT);

		float speed = (float) Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo) * KMH;
		shownSpeed = Mth.lerp(0.25F, shownSpeed, speed);
		graphics.text(font, Component.translatable("outbreak.dashboard.speed", Math.round(shownSpeed)), left + 7, top + 7, PaperStyle.INK, false);

		// Tires: one mark per wheel, laid out as on the vehicle (front at the top).
		int tx = left + WIDTH - 20;
		int ty = top + 6;
		for (int wheel = 0; wheel < type.wheels; wheel++) {
			int x = type.isCar() ? tx + (wheel % 2) * 9 : tx + 4;
			int y = type.isCar() ? ty + (wheel / 2) * 9 : ty + wheel * 9;
			int color = switch (vehicle.tire(wheel)) {
				case GOOD -> PaperStyle.INK;
				case WORN -> PaperStyle.PENCIL;
				case FLAT -> PaperStyle.MARKER_RED;
				case MISSING -> 0;
			};
			graphics.outline(x, y, 5, 7, color == 0 ? PaperStyle.MARKER_RED : PaperStyle.INK);
			if (color != 0) {
				graphics.fill(x + 1, y + 1, x + 4, y + 6, color);
			}
		}

		if (!type.hasEngine) {
			return;
		}

		// Fuel gauge.
		int gx = left + 7;
		int gy = top + 20;
		float fuel = vehicle.fuel() / type.tankSize;
		graphics.outline(gx, gy, 52, 6, PaperStyle.INK);
		graphics.fill(gx + 1, gy + 1, gx + 1 + Math.round(50 * fuel), gy + 5, fuel < 0.15F ? PaperStyle.MARKER_RED : PaperStyle.PENCIL);
		graphics.text(font, String.format(Locale.ROOT, "%.0f L", vehicle.fuel()), gx + 56, gy - 1, PaperStyle.INK, false);

		Component engine = Component.translatable(vehicle.isRunning() ? "outbreak.dashboard.engine_on" : "outbreak.dashboard.engine_off");
		graphics.text(font, engine, left + 7, top + 30, vehicle.isRunning() ? PaperStyle.INK : PaperStyle.PENCIL, false);
		float charge = vehicle.batteryCharge();
		Component battery = charge < 0.0F ? Component.translatable("outbreak.dashboard.no_battery")
			: Component.translatable("outbreak.dashboard.battery", Math.round(charge * 100));
		graphics.text(font, battery, left + WIDTH - 7 - font.width(battery), top + 30, charge < 0.1F ? PaperStyle.MARKER_RED : PaperStyle.PENCIL, false);
	}
}
