package io.github.ditsjohann.outbreak.inventory;

import io.github.ditsjohann.outbreak.item.BagItem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A container screen's menu: the container's grid (laid out by its {@link ContainerKind}) and the
 * survivor's 18 slots: 9 pockets and the hotbar. The locked inventory rows aren't part of it at all.
 */
public class OutbreakMenu extends AbstractContainerMenu {
	private final Container container;
	private final ContainerKind kind;

	public OutbreakMenu(MenuType<?> type, int containerId, Inventory inventory, Container container, ContainerKind kind) {
		super(type, containerId);
		checkContainerSize(container, kind.size());
		this.container = container;
		this.kind = kind;
		container.startOpen(inventory.player);

		for (int row = 0; row < kind.rows(); row++) {
			for (int col = 0; col < kind.columns(); col++) {
				this.addSlot(new Slot(container, col + row * kind.columns(), kind.containerLeft() + col * 18, kind.containerTop() + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						// No bags inside bags.
						return !OutbreakMenu.this.kind.isBag() || !(stack.getItem() instanceof BagItem);
					}
				});
			}
		}

		for (int i = 0; i < 9; i++) {
			this.addSlot(new Slot(inventory, 9 + i, 8 + i * 18, kind.pocketsTop()));
		}

		for (int i = 0; i < 9; i++) {
			this.addSlot(new Slot(inventory, i, 8 + i * 18, kind.hotbarTop()));
		}
	}

	public ContainerKind kind() {
		return this.kind;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			int containerEnd = this.kind.size();
			if (slotIndex < containerEnd) {
				if (!this.moveItemStackTo(stack, containerEnd, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, containerEnd, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return clicked;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}
}
