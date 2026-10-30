package io.github.ditsjohann.outbreak.client;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.client.menu.CharacterSheetScreen;
import io.github.ditsjohann.outbreak.client.menu.MenuClient;
import io.github.ditsjohann.outbreak.client.vehicle.VehicleClient;
import io.github.ditsjohann.outbreak.client.world.BroadcastClient;
import io.github.ditsjohann.outbreak.client.world.MetaClient;
import io.github.ditsjohann.outbreak.client.walker.BodyPartRenderer;
import io.github.ditsjohann.outbreak.client.walker.WalkerModel;
import io.github.ditsjohann.outbreak.client.walker.WalkerRenderer;
import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.gore.Limb;
import io.github.ditsjohann.outbreak.inventory.ContainerKind;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenus;
import io.github.ditsjohann.outbreak.network.OpenBagPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class OutbreakClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Outbreak.id("outbreak"));
	static final KeyMapping HEALTH_KEY = new KeyMapping("key.outbreak.health", InputConstants.KEY_H, CATEGORY);
	static final KeyMapping BAG_KEY = new KeyMapping("key.outbreak.open_bag", InputConstants.KEY_B, CATEGORY);
	static final KeyMapping SHEET_KEY = new KeyMapping("key.outbreak.character_sheet", InputConstants.KEY_K, CATEGORY);

	@Override
	public void onInitializeClient() {
		// Walkers: their own jointed model, skins and hit physics.
		ModelLayerRegistry.registerModelLayer(WalkerRenderer.LAYER, WalkerModel::layer);
		EntityRenderers.register(OutbreakEntities.WALKER, WalkerRenderer::new);
		// Severed limbs and heads, built from the walker model.
		for (Limb limb : Limb.values()) {
			ModelLayerRegistry.registerModelLayer(BodyPartRenderer.LAYERS.get(limb), () -> WalkerModel.pieceLayer(limb));
		}

		EntityRenderers.register(OutbreakEntities.BODY_PART, BodyPartRenderer::new);

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
		KeyMappingHelper.registerKeyMapping(SHEET_KEY);

		// The title screen, the new survivor screens, character creation and the sandbox editor.
		MenuClient.register();
		BroadcastClient.register();
		MetaClient.register();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			BroadcastClient.tick();
			while (HEALTH_KEY.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new HealthPanelScreen());
				}
			}

			while (SHEET_KEY.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new CharacterSheetScreen(SHEET_KEY));
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
