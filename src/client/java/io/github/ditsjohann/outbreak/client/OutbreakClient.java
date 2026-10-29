package io.github.ditsjohann.outbreak.client;

import io.github.ditsjohann.outbreak.entity.OutbreakEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ZombieRenderer;

public class OutbreakClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Walkers borrow the zombie model until they get their own.
		EntityRenderers.register(OutbreakEntities.WALKER, ZombieRenderer::new);

		// No XP in the outbreak. Hearts and hunger stay until the body panel and moodles replace them.
		HudElementRegistry.removeElement(VanillaHudElements.EXPERIENCE_LEVEL);
		HudElementRegistry.removeElement(VanillaHudElements.INFO_BAR);
	}
}
