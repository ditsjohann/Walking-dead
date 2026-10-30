package io.github.ditsjohann.outbreak.mixin;

import io.github.ditsjohann.outbreak.sandbox.Sandbox;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.MobCategory;

/** The monster cap (how many walkers roam around the players) follows the sandbox population setting. */
@Mixin(MobCategory.class)
abstract class MobCategoryMixin {
	@Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
	private void outbreak$population(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this == MobCategory.MONSTER) {
			cir.setReturnValue(Math.round(cir.getReturnValueI() * Sandbox.current().populationMultiplier()));
		}
	}
}
