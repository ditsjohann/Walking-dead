package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.combat.GunClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

/** Aiming a gun zooms in a little; the rifle's scope a lot (see {@link GunClient}). */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void outbreak$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof LocalPlayer player && firstPerson) {
			float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			cir.setReturnValue(cir.getReturnValueF() * GunClient.fov(player, partialTicks));
		}
	}
}
