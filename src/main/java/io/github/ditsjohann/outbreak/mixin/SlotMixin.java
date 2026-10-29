package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.inventory.Pockets;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Locked inventory slots refuse everything and are hidden, in every menu. */
@Mixin(Slot.class)
abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Shadow
	public abstract int getContainerSlot();

	/** Inactive slots aren't drawn, hovered or clicked in any screen: the locked rows simply aren't there. */
	@Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
	private void outbreak$hideLockedSlot(CallbackInfoReturnable<Boolean> cir) {
		if (this.container instanceof Inventory && Pockets.isLocked(this.getContainerSlot())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void outbreak$lockedSlot(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (this.container instanceof Inventory && Pockets.isLocked(this.getContainerSlot())) {
			cir.setReturnValue(false);
		}
	}
}
