package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.combat.SwingAnimations;
import io.github.ditsjohann.outbreak.client.vehicle.RiderPose;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Third person: our swings, punches and the bat's two-handed grip (see {@link SwingAnimations}), and
 * how people sit on our vehicles (see {@link RiderPose}).
 */
@Mixin(HumanoidModel.class)
abstract class HumanoidModelMixin {
	@Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
	private void outbreak$weaponAnimation(HumanoidRenderState state, CallbackInfo ci) {
		if (SwingAnimations.thirdPerson((HumanoidModel<?>) (Object) this, state)) {
			ci.cancel();
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
	private void outbreak$riderPose(HumanoidRenderState state, CallbackInfo ci) {
		RiderPose.apply((HumanoidModel<?>) (Object) this, state);
	}
}
