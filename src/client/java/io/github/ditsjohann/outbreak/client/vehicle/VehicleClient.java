package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.ditsjohann.outbreak.client.combat.GunClient;
import io.github.ditsjohann.outbreak.vehicle.MechanicsMenu;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleActionPayload;
import io.github.ditsjohann.outbreak.vehicle.VehicleImpactPayload;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;
import io.github.ditsjohann.outbreak.vehicle.Vehicles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.Entity;

/**
 * Vehicles on the client: models, the driver's controls (engine on R or click the ignition, horn on
 * G), engine sounds, the mechanics screen.
 */
public final class VehicleClient {
	private static final Map<Integer, EngineSound> SOUNDS = new HashMap<>();

	private VehicleClient() {
	}

	public static void register(KeyMapping.Category category) {
		KeyMapping engineKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.outbreak.engine", InputConstants.KEY_R, category));
		KeyMapping hornKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.outbreak.horn", InputConstants.KEY_G, category));
		for (VehicleType type : VehicleType.values()) {
			ModelLayerRegistry.registerModelLayer(VehicleRenderer.LAYERS.get(type), () -> VehicleModel.load(type));
			EntityRenderers.register(Vehicles.entityType(type), VehicleRenderer::new);
		}

		MenuScreens.register(MechanicsMenu.TYPE, MechanicsScreen::new);
		VehicleActionPayload.sender = ClientPlayNetworking::send;
		ClientPlayNetworking.registerGlobalReceiver(VehicleImpactPayload.TYPE, (payload, context) -> {
			if (context.player().getVehicle() instanceof Vehicle vehicle) {
				vehicle.impact(payload.keep());
			}
		});

		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			LocalPlayer player = minecraft.player;
			if (player != null && player.getVehicle() instanceof Vehicle vehicle && vehicle.getControllingPassenger() == player) {
				var keys = player.input.keyPresses;
				vehicle.setInput(keys.left(), keys.right(), keys.forward(), keys.backward(), keys.jump());
			}

			Ignition.tick(minecraft);
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (engineKey.consumeClick()) {
				// The same key reloads a gun (when you're not in a vehicle).
				if (!GunClient.reload(minecraft)) {
					send(minecraft, VehicleActionPayload.ENGINE);
				}
			}

			while (hornKey.consumeClick()) {
				send(minecraft, VehicleActionPayload.HORN);
			}

			tickSounds(minecraft);
		});
	}

	private static void send(Minecraft minecraft, int action) {
		if (minecraft.player != null && minecraft.player.getVehicle() instanceof Vehicle) {
			ClientPlayNetworking.send(new VehicleActionPayload(action, 0.0F));
		}
	}

	/** Starts an engine sound for every running engine nearby that doesn't have one yet. */
	private static void tickSounds(Minecraft minecraft) {
		SOUNDS.values().removeIf(EngineSound::isStopped);
		if (minecraft.level == null || minecraft.player == null) {
			SOUNDS.clear();
			return;
		}

		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof Vehicle vehicle && vehicle.isRunning() && !SOUNDS.containsKey(vehicle.getId())
				&& vehicle.distanceToSqr(minecraft.player) < 48 * 48) {
				EngineSound sound = new EngineSound(vehicle);
				SOUNDS.put(vehicle.getId(), sound);
				minecraft.getSoundManager().play(sound);
			}
		}
	}
}
