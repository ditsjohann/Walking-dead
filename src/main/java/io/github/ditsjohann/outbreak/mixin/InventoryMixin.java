package io.github.ditsjohann.outbreak.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.github.ditsjohann.outbreak.inventory.Pockets;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;

/** New items only ever go into the hotbar and the 9 pockets, never into the locked slots. */
@Mixin(Inventory.class)
abstract class InventoryMixin {
	@WrapOperation(method = {"getFreeSlot", "getSlotWithRemainingSpace"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;size()I"))
	private int outbreak$onlyPockets(NonNullList<?> items, Operation<Integer> original) {
		return Math.min(original.call(items), Pockets.SLOTS);
	}
}
