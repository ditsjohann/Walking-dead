package io.github.ditsjohann.outbreak.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.vehicle.VehicleClient;
import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.inventory.ContainerKind;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenus;
import io.github.ditsjohann.outbreak.network.OpenBagPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ZombieRenderer;

public class OutbreakClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Outbreak.id("outbreak"));
	static final KeyMapping HEALTH_KEY = new KeyMapping("key.outbreak.health", InputConstants.KEY_H, CATEGORY);
	static final KeyMapping BAG_KEY = new KeyMapping("key.outbreak.open_bag", InputConstants.KEY_B, CATEGORY);

	@Override
	public void onInitializeClient() {
		// Walkers borrow the zombie model until they get their own.
		EntityRenderers.register(OutbreakEntities.WALKER, ZombieRenderer::new);

		// The vanilla HUD is replaced entirely by our own.
		OutbreakHud.register();

		// Every container kind gets its own screen.
		for (ContainerKind kind : ContainerKind.values()) {
			MenuScreens.register(OutbreakMenus.type(kind), OutbreakContainerScreen::new);
		}

		// Vehicles: models, driving controls, engine sounds, the mechanics screen.
		VehicleClient.register(CATEGORY);

		KeyMappingHelper.registerKeyMapping(HEALTH_KEY);
		KeyMappingHelper.registerKeyMapping(BAG_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (HEALTH_KEY.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new HealthPanelScreen());
				}
			}

			while (BAG_KEY.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					ClientPlayNetworking.send(new OpenBagPayload());
				}
			}
		});
	}
}
