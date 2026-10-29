package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.survivor.Hydration;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

@Mixin(Consumable.class)
abstract class ConsumableMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void outbreak$hydrate(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (user instanceof ServerPlayer player) {
			Hydration.onConsume(player, stack);
		}
	}
}
