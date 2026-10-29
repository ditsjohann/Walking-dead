package io.github.ditsjohann.outbreak.block;

import io.github.ditsjohann.outbreak.inventory.ContainerKind;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenu;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Contents of a {@link StorageBlock}; its size and screen come from the block's {@link ContainerKind}. */
public class StorageBlockEntity extends RandomizableContainerBlockEntity {
	private NonNullList<ItemStack> items;

	public StorageBlockEntity(BlockPos pos, BlockState state) {
		super(OutbreakBlocks.STORAGE, pos, state);
		this.items = NonNullList.withSize(this.kind().size(), ItemStack.EMPTY);
	}

	public ContainerKind kind() {
		return ((StorageBlock) this.getBlockState().getBlock()).kind();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!this.trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, this.items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, this.items);
		}
	}

	@Override
	public int getContainerSize() {
		return this.kind().size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected Component getDefaultName() {
		return this.kind().title();
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new OutbreakMenu(OutbreakMenus.type(this.kind()), containerId, inventory, this, this.kind());
	}
}
