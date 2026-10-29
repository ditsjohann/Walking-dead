package io.github.ditsjohann.outbreak.vehicle;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Creative only: puts down a vehicle in good shape, half a tank, and hands you its key. */
public class VehicleSpawnItem extends Item {
	private final VehicleType type;

	public VehicleSpawnItem(VehicleType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable("item.outbreak.vehicle_spawner", Component.translatable("entity.outbreak." + this.type.id()));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}

		Player player = context.getPlayer();
		float yaw = player != null ? player.getYRot() : 0.0F;
		Vehicle vehicle = Vehicles.create(level, this.type, Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace())), yaw);
		VehicleSpawner.prepare(vehicle, VehicleSpawner.Condition.NEW, level.getRandom());
		if (!level.noCollision(vehicle, vehicle.getBoundingBox())) {
			return InteractionResult.FAIL;
		}

		level.addFreshEntity(vehicle);
		if (player != null && this.type.needsKey) {
			player.getInventory().placeItemBackInInventory(CarKeyItem.forVehicle(vehicle), Prediction.SERVER_ONLY);
		}

		if (player == null || !player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}

		return InteractionResult.SUCCESS;
	}
}
