package io.github.ditsjohann.outbreak;

import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.survivor.StarterKit;
import io.github.ditsjohann.outbreak.survivor.Turning;
import io.github.ditsjohann.outbreak.vanilla.VanillaRules;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Outbreak implements ModInitializer {
	public static final String MOD_ID = "outbreak";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		OutbreakEntities.register();
		VanillaRules.register();
		Turning.register();
		StarterKit.register();
		LOGGER.info("Outbreak loaded. Good luck out there.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
