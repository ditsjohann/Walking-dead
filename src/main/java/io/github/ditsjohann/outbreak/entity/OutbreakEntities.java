package io.github.ditsjohann.outbreak.entity;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.gore.BodyPart;

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

	public static final ResourceKey<EntityType<?>> BODY_PART_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Outbreak.id("body_part"));
	/** A severed limb or head, lying where it landed. */
	public static final EntityType<BodyPart> BODY_PART = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		BODY_PART_KEY,
		EntityType.Builder.<BodyPart>of(BodyPart::new, MobCategory.MISC)
			.sized(0.4F, 0.25F)
			.clientTrackingRange(6)
			.updateInterval(2)
			.build(BODY_PART_KEY)
	);

	private OutbreakEntities() {
	}

	public static void register() {
		WalkerCommand.register();
		FabricDefaultAttributeRegistry.register(WALKER, Walker.createAttributes());
		SpawnPlacements.register(WALKER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Walker::checkWalkerSpawnRules);
	}
}
