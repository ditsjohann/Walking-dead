package io.github.ditsjohann.outbreak.vehicle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mojang.serialization.Codec;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.OutbreakEntities;
import io.github.ditsjohann.outbreak.entity.Walker;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Parked cars and wrecks on the roads. The first time a chunk loads, if it has enough road on it
 * (Arnis's asphalt), it may get a vehicle; red bicycle paths may get a bicycle. Most need work
 * before they drive, and the key may be in the ignition, in the trunk, in a walker's pocket nearby,
 * or nowhere at all.
 */
public final class VehicleSpawner {
	/** Set on chunks that have had their turn. */
	public static final AttachmentType<Boolean> CHUNK_DONE = AttachmentRegistry.createPersistent(Outbreak.id("vehicles_placed"), Codec.BOOL);
	/** Things a walker carries besides its loot table: a car key, for one. Dropped when it dies. */
	public static final AttachmentType<List<ItemStack>> CARRIED = AttachmentRegistry.createPersistent(Outbreak.id("carried"), ItemStack.CODEC.listOf());

	public static final float CAR_CHANCE = 0.35F;
	public static final float BICYCLE_CHANCE = 0.2F;
	private static final int MIN_ROAD_BLOCKS = 48;
	private static final int MIN_CYCLE_PATH_BLOCKS = 10;
	private static final int CHUNKS_PER_TICK = 4;
	private static final ResourceKey<LootTable> TRUNK_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Outbreak.id("containers/trunk"));

	private static final Deque<ChunkPos> PENDING = new ArrayDeque<>();

	public enum Condition {
		/** Creative spawns: everything works, half a tank. */
		NEW,
		/** Left behind: worn parts, some fuel, often locked. */
		ABANDONED,
		/** Burnt out or crashed: won't drive again, but has parts and maybe fuel to take. */
		WRECK
	}

	private VehicleSpawner() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() == Level.OVERWORLD && !chunk.hasAttached(CHUNK_DONE)) {
				PENDING.add(chunk.getPos());
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() != Level.OVERWORLD) {
				return;
			}

			for (int i = 0; i < CHUNKS_PER_TICK && !PENDING.isEmpty(); i++) {
				ChunkPos pos = PENDING.poll();
				LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
				if (chunk != null && !chunk.hasAttached(CHUNK_DONE)) {
					chunk.setAttached(CHUNK_DONE, true);
					populate(level, chunk, RandomSource.create(level.getSeed() ^ pos.pack()), CAR_CHANCE, BICYCLE_CHANCE);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			List<ItemStack> carried = entity.getAttached(CARRIED);
			if (carried != null && entity.level() instanceof ServerLevel level) {
				carried.forEach(stack -> entity.spawnAtLocation(level, stack.copy()));
				entity.removeAttached(CARRIED);
			}
		});
	}

	/** Maybe puts a vehicle on this chunk's roads. Returns how many it placed. */
	public static int populate(ServerLevel level, LevelChunk chunk, RandomSource random, float carChance, float bicycleChance) {
		List<BlockPos> road = new ArrayList<>();
		List<BlockPos> cyclePath = new ArrayList<>();
		ChunkPos chunkPos = chunk.getPos();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int worldX = chunkPos.getMinBlockX() + x;
				int worldZ = chunkPos.getMinBlockZ() + z;
				BlockPos ground = new BlockPos(worldX, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1, worldZ);
				if (level.getBlockState(ground).is(Vehicles.ROAD_SURFACE)) {
					road.add(ground);
				} else if (level.getBlockState(ground).is(Vehicles.CYCLE_PATH)) {
					cyclePath.add(ground);
				}
			}
		}

		int placed = 0;
		if (road.size() >= MIN_ROAD_BLOCKS && random.nextFloat() < carChance) {
			VehicleType type = pick(random);
			Condition condition = random.nextFloat() < 0.4F ? Condition.WRECK : Condition.ABANDONED;
			if (place(level, type, condition, road, random)) {
				placed++;
			}
		}

		if (cyclePath.size() >= MIN_CYCLE_PATH_BLOCKS && random.nextFloat() < bicycleChance) {
			if (place(level, VehicleType.BICYCLE, Condition.ABANDONED, cyclePath, random)) {
				placed++;
			}
		}

		return placed;
	}

	private static VehicleType pick(RandomSource random) {
		int roll = random.nextInt(100);
		if (roll < 35) {
			return VehicleType.HATCHBACK;
		} else if (roll < 65) {
			return VehicleType.SEDAN;
		} else if (roll < 80) {
			return VehicleType.VAN;
		}

		return VehicleType.SCOOTER;
	}

	/** Tries a few spots on the surface, lined up with the road. */
	private static boolean place(ServerLevel level, VehicleType type, Condition condition, List<BlockPos> surface, RandomSource random) {
		for (int attempt = 0; attempt < 8; attempt++) {
			BlockPos ground = surface.get(random.nextInt(surface.size()));
			int alongX = run(level, ground, 1, 0);
			int alongZ = run(level, ground, 0, 1);
			float yaw = (alongX > alongZ ? 90.0F : 0.0F) + (random.nextBoolean() ? 180.0F : 0.0F) + (random.nextFloat() - 0.5F) * 16.0F;
			Vehicle vehicle = Vehicles.create(level, type, Vec3.atBottomCenterOf(ground.above()), yaw);
			if (!level.noCollision(vehicle, vehicle.getBoundingBox())) {
				continue;
			}

			prepare(vehicle, condition, random);
			level.addFreshEntity(vehicle);
			if (type.needsKey && condition == Condition.ABANDONED) {
				placeKey(level, vehicle, random);
			}

			return true;
		}

		return false;
	}

	/** Road blocks in a line through pos (up to 4 each way). */
	private static int run(ServerLevel level, BlockPos pos, int dx, int dz) {
		int count = 0;
		for (int i = -4; i <= 4; i++) {
			if (level.getBlockState(pos.offset(dx * i, 0, dz * i)).is(Vehicles.ROAD_SURFACE)
				|| level.getBlockState(pos.offset(dx * i, 0, dz * i)).is(Vehicles.CYCLE_PATH)) {
				count++;
			}
		}

		return count;
	}

	/** Parts, fuel and damage for a vehicle in this condition. */
	public static void prepare(Vehicle vehicle, Condition condition, RandomSource random) {
		VehicleType type = vehicle.vehicleType();
		vehicle.setColor(random.nextInt(type.colors.size()));
		vehicle.fitNewParts();
		switch (condition) {
			case NEW -> vehicle.setFuel(type.tankSize * 0.5F);
			case ABANDONED -> {
				for (int wheel = 0; wheel < type.wheels; wheel++) {
					if (random.nextFloat() < 0.1F) {
						vehicle.parts().setItem(wheel, ItemStack.EMPTY);
					} else {
						wear(vehicle.parts().getItem(wheel), random.nextFloat() < 0.1F ? 1.0F : 0.7F * random.nextFloat());
					}
				}

				if (type.hasBattery) {
					if (random.nextFloat() < 0.15F) {
						vehicle.parts().setItem(type.batterySlot(), ItemStack.EMPTY);
					} else {
						CarBatteryItem.setCharge(vehicle.parts().getItem(type.batterySlot()), random.nextFloat() < 0.25F ? 0.02F : random.nextFloat());
					}
				}

				vehicle.setEngine(random.nextFloat() < 0.2F ? 15 + random.nextInt(25) : 40 + random.nextInt(61));
				vehicle.setFuel(random.nextFloat() < 0.3F ? random.nextFloat() * 2.0F : type.tankSize * 0.6F * random.nextFloat());
				vehicle.setWindows(random.nextFloat() < 0.1F ? random.nextInt(60) : 100);
				vehicle.setBody(50 + random.nextInt(51));
				vehicle.setLocked(random.nextFloat() < 0.7F);
				vehicle.setHasAlarm(random.nextFloat() < 0.25F);
				fillTrunk(vehicle, random);
			}
			case WRECK -> {
				for (int wheel = 0; wheel < type.wheels; wheel++) {
					if (random.nextBoolean()) {
						vehicle.parts().setItem(wheel, ItemStack.EMPTY);
					} else {
						wear(vehicle.parts().getItem(wheel), random.nextBoolean() ? 1.0F : 0.5F + 0.5F * random.nextFloat());
					}
				}

				if (type.hasBattery) {
					if (random.nextFloat() < 0.7F) {
						vehicle.parts().setItem(type.batterySlot(), ItemStack.EMPTY);
					} else {
						CarBatteryItem.setCharge(vehicle.parts().getItem(type.batterySlot()), 0.1F * random.nextFloat());
					}
				}

				vehicle.setFuel(random.nextFloat() < 0.5F ? 0.0F : type.tankSize * 0.35F * random.nextFloat());
				vehicle.setWindows(0);
				vehicle.setBody(5 + random.nextInt(26));
				vehicle.setWreck(true);
				if (random.nextFloat() < 0.5F) {
					fillTrunk(vehicle, random);
				}
			}
		}

		vehicle.parts().setChanged();
	}

	private static void wear(ItemStack part, float amount) {
		part.setDamageValue(Math.round(part.getMaxDamage() * amount));
	}

	private static void fillTrunk(Vehicle vehicle, RandomSource random) {
		if (vehicle.trunk() == null || !(vehicle.level() instanceof ServerLevel level)) {
			return;
		}

		LootTable table = level.getServer().reloadableRegistries().getLootTable(TRUNK_LOOT);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, vehicle.position()).create(LootContextParamSets.CHEST);
		table.fill(vehicle.trunk(), params, random.nextLong());
	}

	/** Where's the key? In the ignition, in the trunk, on a walker nearby, or gone. */
	private static void placeKey(ServerLevel level, Vehicle vehicle, RandomSource random) {
		float roll = random.nextFloat();
		ItemStack key = CarKeyItem.forVehicle(vehicle);
		if (roll < 0.25F) {
			vehicle.setKeyInside(true);
		} else if (roll < 0.4F && vehicle.trunk() != null) {
			vehicle.trunk().setItem(random.nextInt(vehicle.trunk().getContainerSize()), key);
		} else if (roll < 0.65F) {
			Walker walker = OutbreakEntities.WALKER.create(level, EntitySpawnReason.EVENT);
			if (walker == null) {
				return;
			}

			Vec3 at = vehicle.position().add(vehicle.right().scale(vehicle.getBbWidth() * 0.5 + 1.0 + random.nextInt(4)));
			BlockPos ground = BlockPos.containing(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z);
			walker.snapTo(at.x, ground.getY(), at.z, random.nextFloat() * 360.0F, 0.0F);
			walker.setPersistenceRequired();
			walker.setAttached(CARRIED, List.of(key));
			level.addFreshEntity(walker);
		}
	}
}
