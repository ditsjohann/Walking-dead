package io.github.ditsjohann.outbreak.world;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;
import io.github.ditsjohann.outbreak.sandbox.Sandbox;
import io.github.ditsjohann.outbreak.vehicle.JerrycanItem;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleItems;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The pumps at Arnis's petrol stations (a coloured pump head on a white base, a nozzle hanging on
 * each side): right-click a nozzle holding a jerrycan to fill it, or with your car parked next to the
 * pump to fill the tank. Only while there's power, and each pump only holds so much. It's loud.
 */
public final class FuelPumps {
	private static final double REACH = 6.0;
	private static final float NOISE = 12.0F;

	private FuelPumps() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.PASS;
			}

			BlockPos head = head(level.getBlockState(hit.getBlockPos()), hit.getBlockPos(), serverLevel);
			if (head == null) {
				return InteractionResult.PASS;
			}

			use(serverPlayer, serverLevel, head);
			return InteractionResult.SUCCESS;
		});
	}

	/** If this is a pump's nozzle, the pump head it hangs on. */
	public static @Nullable BlockPos head(BlockState state, BlockPos pos, ServerLevel level) {
		if (!state.is(Blocks.TRIPWIRE_HOOK)) {
			return null;
		}

		Direction facing = state.getValue(TripWireHookBlock.FACING);
		BlockPos head = pos.relative(facing.getOpposite());
		boolean concrete = BuiltInRegistries.BLOCK.getKey(level.getBlockState(head).getBlock()).getPath().endsWith("_concrete");
		return concrete && level.getBlockState(head.below()).is(Blocks.CONCRETE.pick(DyeColor.WHITE)) ? head : null;
	}

	private static void use(ServerPlayer player, ServerLevel level, BlockPos head) {
		if (!Timeline.isPowerOn()) {
			player.sendOverlayMessage(Component.translatable("outbreak.pump.no_power"));
			return;
		}

		Reserves reserves = Reserves.get(level);
		float left = reserves.left(head, level);
		if (left <= 0.05F) {
			player.sendOverlayMessage(Component.translatable("outbreak.pump.empty"));
			return;
		}

		float pumped;
		Component message;
		ItemStack can = jerrycan(player);
		Vehicle vehicle = can == null ? vehicle(level, head) : null;
		if (can != null) {
			pumped = Math.min(left, JerrycanItem.CAPACITY - JerrycanItem.fuel(can));
			JerrycanItem.setFuel(can, JerrycanItem.fuel(can) + pumped);
			message = Component.translatable("outbreak.pump.filled_can", litres(pumped), litres(left - pumped));
		} else if (vehicle != null) {
			pumped = Math.min(left, vehicle.vehicleType().tankSize - vehicle.fuel());
			vehicle.setFuel(vehicle.fuel() + pumped);
			message = Component.translatable("outbreak.pump.filled_tank", litres(pumped), litres(left - pumped));
		} else {
			player.sendOverlayMessage(Component.translatable("outbreak.pump.how", litres(left)));
			return;
		}

		if (pumped <= 0.05F) {
			player.sendOverlayMessage(Component.translatable("outbreak.pump.full"));
			return;
		}

		reserves.take(head, pumped);
		level.playSound(null, head, OutbreakSounds.FUEL_PUMP.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		NoiseSystem.emit(level, Vec3.atCenterOf(head), NOISE, player);
		player.sendOverlayMessage(message);
	}

	private static @Nullable ItemStack jerrycan(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).is(VehicleItems.JERRYCAN)) {
				return player.getItemInHand(hand);
			}
		}

		return null;
	}

	/** The nearest vehicle with a tank parked by the pump. */
	private static @Nullable Vehicle vehicle(ServerLevel level, BlockPos head) {
		List<Vehicle> near = level.getEntitiesOfClass(Vehicle.class, new AABB(head).inflate(REACH),
			v -> v.vehicleType().hasEngine && !v.isWreck());
		Vec3 at = Vec3.atCenterOf(head);
		return near.stream().min((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at))).orElse(null);
	}

	private static String litres(float litres) {
		return String.format(Locale.ROOT, "%.0f", litres);
	}

	/** How much fuel is left in each pump (by its head), decided the first time it's used. */
	public static final class Reserves extends SavedData {
		private static final Codec<Reserves> CODEC = Codec.unboundedMap(Codec.STRING, Codec.FLOAT)
			.xmap(Reserves::new, r -> r.left);
		private static final SavedDataType<Reserves> TYPE = new SavedDataType<>(Outbreak.id("fuel_pumps"), Reserves::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final Map<String, Float> left;

		private Reserves() {
			this(Map.of());
		}

		private Reserves(Map<String, Float> left) {
			this.left = new HashMap<>(left);
		}

		static Reserves get(ServerLevel level) {
			return level.getServer().getDataStorage().computeIfAbsent(TYPE);
		}

		float left(BlockPos head, ServerLevel level) {
			return this.left.computeIfAbsent(key(head), k -> {
				this.setDirty();
				// Some went to the people who fled first; later scenarios find less.
				return (80.0F + level.getRandom().nextFloat() * 220.0F) * Math.min(1.0F, Sandbox.current().lootMultiplier());
			});
		}

		void take(BlockPos head, float litres) {
			this.left.merge(key(head), -litres, Float::sum);
			this.setDirty();
		}

		private static String key(BlockPos pos) {
			return pos.getX() + "," + pos.getY() + "," + pos.getZ();
		}
	}
}
