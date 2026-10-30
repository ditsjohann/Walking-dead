package io.github.ditsjohann.outbreak.client.vehicle;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.vehicle.CarKeyItem;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleActionPayload;
import io.github.ditsjohann.outbreak.vehicle.VehicleItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The ignition you can click: sitting in the driver's seat in first person, look at it and
 * right-click to turn the key: start the engine, or switch it off. If the key isn't in it but you
 * have it, it goes in; with no key at all you try to hotwire it. Left-click pulls the key out. A note
 * under the crosshair says what a click will do.
 */
public final class Ignition {
	/** How close (blocks) your line of sight has to pass to the ignition. */
	private static final double REACH = 0.11;

	private static @Nullable Vehicle looking;

	private Ignition() {
	}

	static void tick(Minecraft minecraft) {
		looking = target(minecraft);
		if (looking == null) {
			return;
		}

		boolean turn = false;
		boolean take = false;
		while (minecraft.options.keyUse.consumeClick()) {
			turn = true;
		}

		while (minecraft.options.keyAttack.consumeClick()) {
			take = true;
		}

		if (turn || take) {
			ClientPlayNetworking.send(new VehicleActionPayload(VehicleActionPayload.IGNITION, take ? 1.0F : 0.0F));
		}
	}

	/** The vehicle whose ignition you're looking at, if you're its driver, in first person. */
	private static @Nullable Vehicle target(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || !minecraft.options.getCameraType().isFirstPerson()
			|| !(player.getVehicle() instanceof Vehicle vehicle) || vehicle.getControllingPassenger() != player
			|| !vehicle.vehicleType().hasEngine) {
			return null;
		}

		Vec3 ignition = VehicleRenderer.ignition(vehicle, 1.0F);
		if (ignition == null) {
			return null;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 to = ignition.subtract(eye);
		double along = to.dot(look);
		if (along <= 0.0 || along > 2.0) {
			return null;
		}

		return to.subtract(look.scale(along)).length() < REACH ? vehicle : null;
	}

	/** Whether a click now goes to the ignition. */
	public static boolean isLooking() {
		return looking != null;
	}

	/** The note under the crosshair. */
	public static void extract(GuiGraphicsExtractor graphics, Font font, LocalPlayer player) {
		Vehicle vehicle = looking;
		if (vehicle == null) {
			return;
		}

		Component action;
		if (vehicle.isRunning()) {
			action = Component.translatable("outbreak.ignition.stop");
		} else if (vehicle.isKeyInside() || vehicle.isHotwired()) {
			action = Component.translatable("outbreak.ignition.start");
		} else if (CarKeyItem.find(player, vehicle) != null) {
			action = Component.translatable("outbreak.ignition.insert");
		} else if (player.getMainHandItem().is(VehicleItems.SCREWDRIVER)) {
			action = Component.translatable("outbreak.ignition.hotwire");
		} else {
			action = Component.translatable("outbreak.ignition.no_key");
		}

		line(graphics, font, action, 0);
		if (vehicle.isKeyInside()) {
			line(graphics, font, Component.translatable("outbreak.ignition.take_key"), 1);
		}
	}

	private static void line(GuiGraphicsExtractor graphics, Font font, Component text, int row) {
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 10 + row * 12;
		int width = font.width(text);
		graphics.fill(x - width / 2 - 3, y - 2, x + width / 2 + 3, y + 10, 0xB0201C18);
		graphics.text(font, text, x - width / 2, y, 0xFFF2E8D0, false);
	}

	/** Whether your hands are on the wheel (or handlebars): first person, driving something with grips. */
	public static boolean handsOnWheel() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !(player.getVehicle() instanceof Vehicle vehicle)
			|| vehicle.getControllingPassenger() != player || player.isUsingItem()) {
			return false;
		}

		VehicleModel model = VehicleRenderer.MODELS.get(vehicle.vehicleType());
		return model != null && model.has("grip_left");
	}
}
