package io.github.ditsjohann.outbreak.vehicle;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The key to one vehicle: starts it; right-click the vehicle to unlock it, sneak-right-click to lock it. */
public class CarKeyItem extends Item {
	public CarKeyItem(Properties properties) {
		super(properties);
	}

	public static ItemStack forVehicle(Vehicle vehicle) {
		ItemStack key = new ItemStack(VehicleItems.CAR_KEY);
		key.set(VehicleComponents.VEHICLE_KEY, new VehicleComponents.VehicleKey(vehicle.getUUID(), vehicle.vehicleType().id(), vehicle.colorName()));
		return key;
	}

	public static boolean fits(ItemStack stack, Vehicle vehicle) {
		VehicleComponents.VehicleKey key = stack.get(VehicleComponents.VEHICLE_KEY);
		return key != null && key.vehicle().equals(vehicle.getUUID());
	}

	/** The player's key to this vehicle, if they carry one. */
	public static @Nullable ItemStack find(Player player, Vehicle vehicle) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (fits(stack, vehicle)) {
				return stack;
			}
		}

		return null;
	}

	@Override
	public Component getName(ItemStack stack) {
		VehicleComponents.VehicleKey key = stack.get(VehicleComponents.VEHICLE_KEY);
		return key == null ? super.getName(stack) : Component.translatable("item.outbreak.car_key.for", key.describe());
	}
}
