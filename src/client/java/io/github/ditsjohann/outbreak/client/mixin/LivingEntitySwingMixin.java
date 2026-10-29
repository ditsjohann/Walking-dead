package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.combat.Punches;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/** Each new bare-handed swing of the local player uses the other fist. */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
	private void outbreak$alternateFists(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValueZ() && hand == InteractionHand.MAIN_HAND && self == Minecraft.getInstance().player && self.getMainHandItem().isEmpty()) {
			Punches.next(self.level().getGameTime());
		}
	}
}
