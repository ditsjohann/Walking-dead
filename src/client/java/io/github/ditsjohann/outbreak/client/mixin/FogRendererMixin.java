package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.character.Perks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.entity.player.Player;

/** The short-sighted trait: everything further than a few dozen blocks is lost in a blur of fog. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	private static final float SHORT_SIGHTED_END = 32.0F;

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void outbreak$shortSighted(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
			CallbackInfoReturnable<FogData> cir) {
		if (camera.entity() instanceof Player player && Perks.isShortSighted(player)) {
			FogData fog = cir.getReturnValue();
			fog.environmentalStart = Math.min(fog.environmentalStart, 4.0F);
			fog.environmentalEnd = Math.min(fog.environmentalEnd, SHORT_SIGHTED_END);
			fog.renderDistanceStart = Math.min(fog.renderDistanceStart, SHORT_SIGHTED_END * 0.8F);
			fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, SHORT_SIGHTED_END);
		}
	}
}
