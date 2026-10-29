package io.github.ditsjohann.outbreak.item;

import java.util.function.Supplier;

import io.github.ditsjohann.outbreak.inventory.BagContainer;
import io.github.ditsjohann.outbreak.inventory.BagMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A bag: right-click to open it, sneak-right-click to put it on your back. Worn, its contents weigh
 * less (by {@link #weightReduction()}); open a worn bag with its key (B by default).
 */
public class BagItem extends Item {
	private final int rows;
	private final float weightReduction;

	public BagItem(int rows, float weightReduction, Properties properties) {
		super(properties);
		this.rows = rows;
		this.weightReduction = weightReduction;
	}

	public float weightReduction() {
		return this.weightReduction;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive()) {
			// Equippable: swaps it onto your back.
			return super.use(level, player, hand);
		}

		if (player instanceof ServerPlayer serverPlayer) {
			ItemStack bag = player.getItemInHand(hand);
			open(serverPlayer, bag, () -> serverPlayer.getItemInHand(hand));
		}

		return InteractionResult.SUCCESS;
	}

	/** Open the bag worn on the player's back, if any. */
	public static void openWorn(ServerPlayer player) {
		ItemStack bag = player.getItemBySlot(EquipmentSlot.CHEST);
		if (bag.getItem() instanceof BagItem) {
			open(player, bag, () -> player.getItemBySlot(EquipmentSlot.CHEST));
		}
	}

	private static void open(ServerPlayer player, ItemStack bag, Supplier<ItemStack> location) {
		BagItem item = (BagItem) bag.getItem();
		int size = item.rows * 9;
		player.openMenu(new SimpleMenuProvider(
			(id, inventory, p) -> new BagMenu(id, inventory, new BagContainer(bag, size, location), item.rows),
			bag.getHoverName()));
		player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
	}
}
