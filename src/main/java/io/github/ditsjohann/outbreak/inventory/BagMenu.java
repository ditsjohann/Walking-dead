package io.github.ditsjohann.outbreak.inventory;

import io.github.ditsjohann.outbreak.item.BagItem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A plain chest menu (so the client needs nothing special), except bags can't go inside bags. */
public class BagMenu extends ChestMenu {
	public BagMenu(int containerId, Inventory inventory, Container container, int rows) {
		super(menuType(rows), containerId, inventory, container, rows);
		for (int i = 0; i < rows * 9; i++) {
			Slot old = this.slots.get(i);
			Slot slot = new Slot(container, old.getContainerSlot(), old.x, old.y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return !(stack.getItem() instanceof BagItem);
				}
			};
			slot.index = i;
			this.slots.set(i, slot);
		}
	}

	private static MenuType<?> menuType(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}
}
