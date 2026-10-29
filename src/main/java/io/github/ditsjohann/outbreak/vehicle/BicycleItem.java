package io.github.ditsjohann.outbreak.vehicle;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** A bicycle you carry. Right-click the ground to put it down; punch it to pick it back up. */
public class BicycleItem extends Item {
	public BicycleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = context.getItemInHand();
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F;
		Vehicle bicycle = Vehicles.create(level, VehicleType.BICYCLE, Vec3.atBottomCenterOf(pos), yaw);
		if (!level.noCollision(bicycle, bicycle.getBoundingBox())) {
			return InteractionResult.FAIL;
		}

		ItemContainerContents parts = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		if (stack.has(VehicleComponents.VEHICLE_COLOR)) {
			bicycle.setColor(stack.getOrDefault(VehicleComponents.VEHICLE_COLOR, 0));
			NonNullList<ItemStack> stacks = NonNullList.withSize(bicycle.parts().getContainerSize(), ItemStack.EMPTY);
			parts.copyInto(stacks);
			for (int i = 0; i < stacks.size(); i++) {
				bicycle.parts().setItem(i, stacks.get(i));
			}
		} else {
			// A new bicycle from the creative menu or a recipe.
			bicycle.setColor(level.getRandom().nextInt(VehicleType.BICYCLE.colors.size()));
			bicycle.fitNewParts();
		}

		level.addFreshEntity(bicycle);
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}

	/** The bicycle as an item, keeping its tires and colour. */
	public static ItemStack pickUp(Vehicle bicycle) {
		ItemStack stack = new ItemStack(VehicleItems.BICYCLE);
		List<ItemStack> parts = new ArrayList<>();
		for (int i = 0; i < bicycle.parts().getContainerSize(); i++) {
			parts.add(bicycle.parts().getItem(i).copy());
			bicycle.parts().setItem(i, ItemStack.EMPTY);
		}

		stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(parts));
		stack.set(VehicleComponents.VEHICLE_COLOR, bicycle.color());
		return stack;
	}
}
