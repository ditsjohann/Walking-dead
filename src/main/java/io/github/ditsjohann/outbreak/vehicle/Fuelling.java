package io.github.ditsjohann.outbreak.vehicle;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Fuel only moves by hand: pour a jerrycan into a tank, or siphon a tank into a jerrycan with a hose
 * (the jerrycan anywhere in your inventory). Pumps at gas stations come later, with the power grid.
 */
public final class Fuelling {
	private Fuelling() {
	}

	public static void pour(ServerPlayer player, Vehicle vehicle, ItemStack can) {
		if (!vehicle.vehicleType().hasEngine) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.no_tank"));
			return;
		}

		float space = vehicle.vehicleType().tankSize - vehicle.fuel();
		float amount = Math.min(JerrycanItem.fuel(can), space);
		if (amount <= 0.01F) {
			player.sendOverlayMessage(Component.translatable(space <= 0.01F ? "outbreak.vehicle.tank_full" : "outbreak.vehicle.can_empty"));
			return;
		}

		JerrycanItem.setFuel(can, JerrycanItem.fuel(can) - amount);
		vehicle.setFuel(vehicle.fuel() + amount);
		player.level().playSound(null, vehicle.getX(), vehicle.getY(), vehicle.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable("outbreak.vehicle.poured", litres(amount), litres(vehicle.fuel()), litres(vehicle.vehicleType().tankSize)));
	}

	public static void siphon(ServerPlayer player, Vehicle vehicle) {
		if (!vehicle.vehicleType().hasEngine) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.no_tank"));
			return;
		}

		ItemStack can = emptiestCan(player);
		if (can == null) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.needs_can"));
			return;
		}

		float amount = Math.min(vehicle.fuel(), JerrycanItem.CAPACITY - JerrycanItem.fuel(can));
		if (amount <= 0.01F) {
			player.sendOverlayMessage(Component.translatable("outbreak.vehicle.tank_empty"));
			return;
		}

		vehicle.setFuel(vehicle.fuel() - amount);
		JerrycanItem.setFuel(can, JerrycanItem.fuel(can) + amount);
		player.getItemInHand(InteractionHand.MAIN_HAND).hurtAndBreak(1, player, InteractionHand.MAIN_HAND.asEquipmentSlot());
		player.level().playSound(null, vehicle.getX(), vehicle.getY(), vehicle.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8F, 0.7F);
		player.sendOverlayMessage(Component.translatable("outbreak.vehicle.siphoned", litres(amount), litres(JerrycanItem.fuel(can))));
	}

	/** The jerrycan with the most room left, or null if every one is full (or there are none). */
	private static @Nullable ItemStack emptiestCan(ServerPlayer player) {
		ItemStack best = null;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof JerrycanItem && JerrycanItem.fuel(stack) < JerrycanItem.CAPACITY - 0.01F
				&& (best == null || JerrycanItem.fuel(stack) < JerrycanItem.fuel(best))) {
				best = stack;
			}
		}

		return best;
	}

	private static String litres(float litres) {
		return String.format(Locale.ROOT, "%.1f", litres);
	}
}
