package io.github.ditsjohann.outbreak;

import io.github.ditsjohann.outbreak.block.OutbreakBlocks;
import io.github.ditsjohann.outbreak.character.Characters;
import io.github.ditsjohann.outbreak.command.OutbreakCommand;
import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.inventory.OutbreakMenus;
import io.github.ditsjohann.outbreak.inventory.Pockets;
import io.github.ditsjohann.outbreak.item.OutbreakItems;
import io.github.ditsjohann.outbreak.loot.ContainerLoot;
import io.github.ditsjohann.outbreak.network.OpenBagPayload;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.survivor.BodySimulation;
import io.github.ditsjohann.outbreak.survivor.Injuries;
import io.github.ditsjohann.outbreak.survivor.Survivors;
import io.github.ditsjohann.outbreak.survivor.Turning;
import io.github.ditsjohann.outbreak.vanilla.VanillaRules;
import io.github.ditsjohann.outbreak.vehicle.Vehicles;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Outbreak implements ModInitializer {
	public static final String MOD_ID = "outbreak";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		OutbreakBlocks.register();
		OutbreakEntities.register();
		VanillaRules.register();
		Turning.register();
		Survivors.register();
		Characters.register();
		OutbreakItems.register();
		Injuries.register();
		BodySimulation.register();
		OutbreakCommand.register();
		ContainerLoot.register();
		Sandbox.register();
		OutbreakMenus.register();
		OpenBagPayload.register();
		Pockets.register();
		Vehicles.register();
		LOGGER.info("Outbreak loaded. Good luck out there.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
