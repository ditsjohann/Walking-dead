package io.github.ditsjohann.outbreak.inventory;

import java.util.function.Supplier;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The inside of a bag, backed by the bag item's container component. Valid only while the very same
 * bag stack is still where it was opened from (hand or back), so it can't be duplicated by moving
 * or dropping the bag while it's open.
 */
public class BagContainer extends SimpleContainer {
	private final ItemStack bag;
	private final Supplier<ItemStack> currentStack;

	public BagContainer(ItemStack bag, int size, Supplier<ItemStack> currentStack) {
		super(size);
		this.bag = bag;
		this.currentStack = currentStack;
		NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
		bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		for (int i = 0; i < size; i++) {
			this.setItem(i, items.get(i));
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.currentStack.get() == this.bag) {
			this.bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return player.isAlive() && this.currentStack.get() == this.bag;
	}
}
