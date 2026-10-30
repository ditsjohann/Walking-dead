package io.github.ditsjohann.outbreak.client.world;

import java.util.HashMap;
import java.util.Map;

import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.world.Helicopter;
import io.github.ditsjohann.outbreak.world.MetaEvents;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

/** Meta events on the client: the helicopter (model and sound), flares in the distance, guts on survivors. */
public final class MetaClient {
	private static final Map<Integer, HelicopterSound> SOUNDS = new HashMap<>();

	private MetaClient() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		ModelLayerRegistry.registerModelLayer(HelicopterRenderer.LAYER, HelicopterModel::load);
		EntityRenderers.register(OutbreakEntities.HELICOPTER, HelicopterRenderer::new);
		ParticleProviderRegistry.getInstance().register(MetaEvents.FLARE, FlareParticle.Provider::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (type == EntityTypes.PLAYER && renderer instanceof AvatarRenderer avatar) {
				helper.register(new GutsLayer(avatar));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(MetaEvents.FlarePayload.TYPE, (payload, context) -> flare(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(MetaClient::tickSounds);
	}

	/**
	 * A flare a long way off: drawn in its direction, but within view distance (it would be lost in the
	 * fog beyond), low over the horizon, with the pop of it going up a moment before.
	 */
	private static void flare(Minecraft minecraft, MetaEvents.FlarePayload payload) {
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}

		Vec3 eye = minecraft.player.getEyePosition();
		Vec3 to = new Vec3(payload.x() - eye.x, 0.0, payload.z() - eye.z);
		double distance = to.length();
		double shown = Math.min(distance, minecraft.options.getEffectiveRenderDistance() * 16.0 * 0.6);
		Vec3 dir = distance < 1.0 ? new Vec3(1.0, 0.0, 0.0) : to.scale(1.0 / distance);
		Vec3 at = eye.add(dir.scale(shown)).add(0.0, shown * 0.12, 0.0);
		minecraft.level.addParticle(MetaEvents.FLARE, true, true, at.x, at.y, at.z, 0.0, shown * 0.12, 0.0);
		minecraft.level.playLocalSound(eye.x + dir.x * 16.0, eye.y + 8.0, eye.z + dir.z * 16.0, SoundEvents.FIREWORK_ROCKET_LAUNCH,
			SoundSource.AMBIENT, 0.5F, 0.6F, false);
	}

	private static void tickSounds(Minecraft minecraft) {
		SOUNDS.values().removeIf(HelicopterSound::isStopped);
		if (minecraft.level == null) {
			SOUNDS.clear();
			return;
		}

		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof Helicopter helicopter && !SOUNDS.containsKey(helicopter.getId())) {
				HelicopterSound sound = new HelicopterSound(helicopter);
				SOUNDS.put(helicopter.getId(), sound);
				minecraft.getSoundManager().play(sound);
			}
		}
	}
}
