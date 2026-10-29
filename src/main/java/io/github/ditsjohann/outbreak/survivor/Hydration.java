package io.github.ditsjohann.outbreak.survivor;

import java.util.Map;

import io.github.ditsjohann.outbreak.item.OutbreakItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** How much drinking (or eating something juicy) quenches thirst. */
public final class Hydration {
	/** A bottle of water straight from a canal can make you sick, and that looks just like the start of the infection. */
	private static final float RAW_WATER_POISON_CHANCE = 0.15F;
	private static final int POISON_TICKS = 20 * 90;

	private static final Map<Item, Float> AMOUNTS = Map.ofEntries(
		Map.entry(Items.POTION, 0.35F),
		Map.entry(Items.MILK_BUCKET, 0.4F),
		Map.entry(Items.HONEY_BOTTLE, 0.1F),
		Map.entry(Items.MELON_SLICE, 0.1F),
		Map.entry(Items.SWEET_BERRIES, 0.03F),
		Map.entry(Items.GLOW_BERRIES, 0.03F),
		Map.entry(Items.APPLE, 0.05F),
		Map.entry(Items.MUSHROOM_STEW, 0.2F),
		Map.entry(Items.BEETROOT_SOUP, 0.2F),
		Map.entry(Items.RABBIT_STEW, 0.2F),
		Map.entry(Items.SUSPICIOUS_STEW, 0.2F),
		Map.entry(OutbreakItems.BOILED_WATER, 0.4F)
	);

	private Hydration() {
	}

	/** Called when a player finishes eating or drinking something. */
	public static void onConsume(ServerPlayer player, ItemStack stack) {
		Float amount = AMOUNTS.get(stack.getItem());
		if (amount == null) {
			return;
		}

		SurvivorState state = Survivors.get(player);
		state.thirst = Math.max(0.0F, state.thirst - amount);
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		if (potion != null && potion.is(Potions.WATER) && player.getRandom().nextFloat() < RAW_WATER_POISON_CHANCE) {
			state.poisonTicks = POISON_TICKS;
		}

		Survivors.sync(player);
	}
}
