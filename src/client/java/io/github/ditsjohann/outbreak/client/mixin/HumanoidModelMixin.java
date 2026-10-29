package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.combat.SwingAnimations;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Third person: our swings, punches and the bat's two-handed grip (see {@link SwingAnimations}). */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
	@Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
	private void outbreak$weaponAnimation(HumanoidRenderState state, CallbackInfo ci) {
		if (SwingAnimations.thirdPerson((HumanoidModel<?>) (Object) this, state)) {
			ci.cancel();
		}
	}
}
