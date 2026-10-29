package io.github.ditsjohann.outbreak.vehicle;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/** Registers everything vehicle: one entity type per {@link VehicleType}, items, menus and packets. */
public final class Vehicles {
	/** What Arnis paves roads with (asphalt mix, lane markings, parking lots and forecourts). */
	public static final TagKey<Block> ROAD_SURFACE = TagKey.create(Registries.BLOCK, Outbreak.id("road_surface"));
	/** Arnis's red bicycle paths. */
	public static final TagKey<Block> CYCLE_PATH = TagKey.create(Registries.BLOCK, Outbreak.id("cycle_path"));

	private static final Map<VehicleType, EntityType<Vehicle>> ENTITY_TYPES = new EnumMap<>(VehicleType.class);
	private static final Map<EntityType<?>, VehicleType> TYPES = new HashMap<>();
	private static final Map<VehicleType, Item> SPAWN_ITEMS = new EnumMap<>(VehicleType.class);

	static {
		for (VehicleType type : VehicleType.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Outbreak.id(type.id()));
			EntityType<Vehicle> entityType = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<Vehicle>of(Vehicle::new, MobCategory.MISC)
					.sized(type.width, type.height)
					.clientTrackingRange(10)
					.build(key));
			ENTITY_TYPES.put(type, entityType);
			TYPES.put(entityType, type);
			if (type != VehicleType.BICYCLE) {
				SPAWN_ITEMS.put(type, VehicleItems.register(type.id() + "_spawner", p -> new VehicleSpawnItem(type, p), new Item.Properties().stacksTo(1)));
			}
		}
	}

	private Vehicles() {
	}

	public static EntityType<Vehicle> entityType(VehicleType type) {
		return ENTITY_TYPES.get(type);
	}

	static VehicleType typeOf(EntityType<?> entityType) {
		VehicleType type = TYPES.get(entityType);
		if (type == null) {
			throw new IllegalArgumentException("Not a vehicle: " + entityType);
		}

		return type;
	}

	public static Item spawnItem(VehicleType type) {
		return type == VehicleType.BICYCLE ? VehicleItems.BICYCLE : SPAWN_ITEMS.get(type);
	}

	/** A new vehicle at pos, facing yaw, not yet added to the level. Empty: no parts, no fuel. */
	public static Vehicle create(ServerLevel level, VehicleType type, Vec3 pos, float yaw) {
		Vehicle vehicle = entityType(type).create(level, EntitySpawnReason.TRIGGERED);
		if (vehicle == null) {
			throw new IllegalStateException("Could not create " + type);
		}

		vehicle.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		return vehicle;
	}

	public static void register() {
		VehicleComponents.register();
		VehicleItems.register();
		VehicleActionPayload.register();
		VehicleImpactPayload.register();
		Outbreak.LOGGER.debug("Registered {}", MechanicsMenu.TYPE);
		VehicleSpawner.register();
		VehicleCommand.register();

		// Sitting in a car, the windows take the walkers' hits until they break.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Player && entity.getVehicle() instanceof Vehicle vehicle
				&& source.getEntity() instanceof Mob && source.getDirectEntity() == source.getEntity()
				&& vehicle.absorbHit(amount)));
	}
}
