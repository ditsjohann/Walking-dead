package io.github.ditsjohann.outbreak.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;

/** No vanilla advancements or their toasts: progress is measured in days survived. */
@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
	@Inject(method = "award", at = @At("HEAD"), cancellable = true)
	private void outbreak$noAdvancements(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
