package io.github.ditsjohann.outbreak.entity;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;

public final class OutbreakEntities {
	private static final ResourceKey<EntityType<?>> WALKER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Outbreak.id("walker"));

	public static final EntityType<Walker> WALKER = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		WALKER_KEY,
		EntityType.Builder.<Walker>of(Walker::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F)
			.eyeHeight(1.74F)
			.passengerAttachments(2.0125F)
			.ridingOffset(-0.7F)
			.clientTrackingRange(8)
			.notInPeaceful()
			.build(WALKER_KEY)
	);

	private OutbreakEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(WALKER, Walker.createAttributes());
		SpawnPlacements.register(WALKER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Walker::checkWalkerSpawnRules);
	}
}
