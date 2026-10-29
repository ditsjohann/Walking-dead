package io.github.ditsjohann.outbreak.inventory;

import java.util.Map;

import io.github.ditsjohann.outbreak.item.BagItem;
import io.github.ditsjohann.outbreak.item.OutbreakItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Everything weighs something (in kg). Carry more than you can and you slow down and tire faster.
 * A worn bag makes its contents lighter; a bag carried in your hands doesn't.
 */
public final class CarryWeight {
	public static final float CAPACITY = 12.0F;

	private static final Map<Item, Float> WEIGHTS = Map.ofEntries(
		Map.entry(OutbreakItems.RAG, 0.05F),
		Map.entry(OutbreakItems.BANDAGE, 0.05F),
		Map.entry(OutbreakItems.DISINFECTANT, 0.3F),
		Map.entry(OutbreakItems.BOILED_WATER, 0.5F),
		Map.entry(OutbreakItems.KITCHEN_KNIFE, 0.4F),
		Map.entry(OutbreakItems.BASEBALL_BAT, 1.0F),
		Map.entry(OutbreakItems.CROWBAR, 2.0F),
		Map.entry(OutbreakItems.MACHETE, 1.0F),
		Map.entry(OutbreakItems.HAMMER, 1.0F),
		Map.entry(OutbreakItems.NAILS, 0.01F),
		Map.entry(OutbreakItems.SCHOOL_BAG, 0.5F),
		Map.entry(OutbreakItems.HIKING_BAG, 1.0F),
		Map.entry(Items.POTION, 0.5F),
		Map.entry(Items.GLASS_BOTTLE, 0.2F),
		Map.entry(Items.BUCKET, 0.8F),
		Map.entry(Items.WATER_BUCKET, 1.8F),
		Map.entry(Items.MILK_BUCKET, 1.8F),
		Map.entry(Items.TORCH, 0.05F),
		Map.entry(Items.STICK, 0.05F)
	);

	private CarryWeight() {
	}

	/** Weight of one item, not counting anything inside it. */
	public static float itemWeight(ItemStack stack) {
		Float weight = WEIGHTS.get(stack.getItem());
		if (weight != null) {
			return weight;
		}

		if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
			return 1.5F;
		}

		if (stack.has(DataComponents.EQUIPPABLE)) {
			return 2.0F;
		}

		if (stack.getItem() instanceof BlockItem) {
			return 0.15F;
		}

		if (stack.has(DataComponents.FOOD)) {
			return 0.15F;
		}

		return 0.05F;
	}

	/** Weight of a whole stack, including what's inside it (scaled by {@code contentsFactor}). */
	public static float stackWeight(ItemStack stack, float contentsFactor) {
		if (stack.isEmpty()) {
			return 0.0F;
		}

		float weight = itemWeight(stack) * stack.getCount();
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			float inside = (float) contents.nonEmptyItemCopyStream().mapToDouble(item -> itemWeight(item) * item.getCount()).sum();
			weight += inside * contentsFactor;
		}

		return weight;
	}

	public static float total(Player player) {
		Inventory inventory = player.getInventory();
		float total = 0.0F;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			boolean wornBag = stack == player.getItemBySlot(EquipmentSlot.CHEST) && stack.getItem() instanceof BagItem;
			float factor = wornBag ? 1.0F - ((BagItem) stack.getItem()).weightReduction() : 1.0F;
			total += stackWeight(stack, factor);
		}

		return total;
	}

	/** 1.0 = exactly at capacity. */
	public static float ratio(Player player) {
		return total(player) / CAPACITY;
	}
}
