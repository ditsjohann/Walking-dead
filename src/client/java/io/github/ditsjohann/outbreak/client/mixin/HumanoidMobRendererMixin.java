package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.vehicle.RiderPose;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;

/** Players and humanoid mobs on our vehicles: which pose (see {@link RiderPose}). */
@Mixin(HumanoidMobRenderer.class)
abstract class HumanoidMobRendererMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void outbreak$riderPose(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver itemModelResolver,
		CallbackInfo ci) {
		RiderPose.extract(entity, state, partialTicks);
	}
}
