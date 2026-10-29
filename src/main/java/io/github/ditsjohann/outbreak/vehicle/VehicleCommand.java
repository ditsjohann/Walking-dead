package io.github.ditsjohann.outbreak.vehicle;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Operator commands for testing vehicles:
 * <pre>
 * /outbreak vehicle spawn &lt;type&gt; &lt;new|abandoned|wreck&gt; [give key]
 * /outbreak vehicle info                     - the nearest vehicle, in detail
 * /outbreak vehicle engine|horn              - as the driver (for test players, who can't press keys)
 * /outbreak vehicle start                    - turn the key (or the wires) only if the engine is off
 * /outbreak vehicle set &lt;fuel|engine|windows|body|battery&gt; &lt;value&gt;
 * /outbreak vehicle populate &lt;radius in chunks&gt; - run the road spawner on loaded chunks, every chunk gets its chance at 100%
 * </pre>
 */
public final class VehicleCommand {
	private VehicleCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("vehicle")
				.then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(VehicleType.values()).map(VehicleType::id), builder))
					.then(Commands.argument("condition", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[] {"new", "abandoned", "wreck"}, builder))
						.executes(ctx -> spawn(ctx, false))
						.then(Commands.argument("key", BoolArgumentType.bool()).executes(ctx -> spawn(ctx, BoolArgumentType.getBool(ctx, "key")))))))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
				.then(Commands.literal("engine").executes(ctx -> drive(ctx.getSource(), VehicleActionPayload.ENGINE)))
				.then(Commands.literal("start").executes(ctx -> drive(ctx.getSource(), -1)))
				.then(Commands.literal("horn").executes(ctx -> drive(ctx.getSource(), VehicleActionPayload.HORN)))
				.then(Commands.literal("set").then(Commands.argument("what", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[] {"fuel", "engine", "windows", "body", "battery", "locked"}, builder))
					.then(Commands.argument("value", FloatArgumentType.floatArg(0.0F)).executes(VehicleCommand::set))))
				.then(Commands.literal("populate").then(Commands.argument("radius", IntegerArgumentType.integer(0, 8)).executes(ctx -> {
					ServerLevel level = ctx.getSource().getLevel();
					ChunkPos center = ChunkPos.containing(net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
					int radius = IntegerArgumentType.getInteger(ctx, "radius");
					int placed = 0;
					for (int x = -radius; x <= radius; x++) {
						for (int z = -radius; z <= radius; z++) {
							LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + x, center.z() + z);
							if (chunk != null) {
								chunk.setAttached(VehicleSpawner.CHUNK_DONE, true);
								placed += VehicleSpawner.populate(level, chunk, level.getRandom(), 1.0F, 1.0F);
							}
						}
					}

					int total = placed;
					ctx.getSource().sendSuccess(() -> Component.literal("Placed " + total + " vehicles"), false);
					return total;
				})))));
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, boolean giveKey) {
		CommandSourceStack source = ctx.getSource();
		VehicleType type;
		VehicleSpawner.Condition condition;
		try {
			type = VehicleType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT));
			condition = VehicleSpawner.Condition.valueOf(StringArgumentType.getString(ctx, "condition").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.literal("Unknown vehicle type or condition"));
			return 0;
		}

		ServerLevel level = source.getLevel();
		float yaw = source.getRotation().y;
		Vehicle vehicle = Vehicles.create(level, type, source.getPosition(), yaw);
		VehicleSpawner.prepare(vehicle, condition, level.getRandom());
		level.addFreshEntity(vehicle);
		if (giveKey && type.needsKey && source.getEntity() instanceof ServerPlayer player) {
			player.getInventory().placeItemBackInInventory(CarKeyItem.forVehicle(vehicle), Prediction.SERVER_ONLY);
		}

		source.sendSuccess(() -> Component.literal("Spawned ").append(vehicle.describe()).append(" (" + condition.name().toLowerCase(Locale.ROOT) + ")"), false);
		return 1;
	}

	private static int drive(CommandSourceStack source, int action) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(player.getVehicle() instanceof Vehicle vehicle)) {
			source.sendFailure(Component.literal("Run this as a player sitting in a vehicle"));
			return 0;
		}

		if (action == VehicleActionPayload.ENGINE || (action < 0 && !vehicle.isRunning())) {
			vehicle.toggleEngine(player);
		} else if (action == VehicleActionPayload.HORN) {
			vehicle.honk(player);
		}

		return info(source, vehicle);
	}

	private static @Nullable Vehicle nearest(CommandSourceStack source) {
		Vec3 pos = source.getPosition();
		List<Vehicle> vehicles = source.getLevel().getEntitiesOfClass(Vehicle.class, new AABB(pos, pos).inflate(8.0));
		return vehicles.stream().min(Comparator.comparingDouble(vehicle -> vehicle.distanceToSqr(pos))).orElse(null);
	}

	private static int info(CommandSourceStack source) {
		Vehicle vehicle = nearest(source);
		if (vehicle == null) {
			source.sendFailure(Component.literal("No vehicle within 8 blocks"));
			return 0;
		}

		return info(source, vehicle);
	}

	private static int info(CommandSourceStack source, Vehicle vehicle) {
		VehicleType type = vehicle.vehicleType();
		StringBuilder text = new StringBuilder();
		text.append(String.format(Locale.ROOT, "%s %s: fuel %.1f/%.0f L, engine %d, battery %.2f, windows %d, body %d",
			vehicle.colorName(), type.id(), vehicle.fuel(), type.tankSize, vehicle.engine(), vehicle.batteryCharge(), vehicle.windows(), vehicle.body()));
		text.append(vehicle.isRunning() ? ", RUNNING" : "").append(vehicle.isLocked() ? ", locked" : "")
			.append(vehicle.hasAlarm() ? ", alarm" : "").append(vehicle.isAlarmSounding() ? " (SOUNDING)" : "")
			.append(vehicle.isHotwired() ? ", hotwired" : "").append(vehicle.isKeyInside() ? ", key inside" : "")
			.append(vehicle.isWreck() ? ", WRECK" : "");
		text.append("\n  tires:");
		for (int wheel = 0; wheel < type.wheels; wheel++) {
			ItemStack tire = vehicle.parts().getItem(wheel);
			text.append(' ').append(tire.isEmpty() ? "none" : (tire.getMaxDamage() - tire.getDamageValue()) + "%");
		}

		if (vehicle.trunk() != null) {
			int items = 0;
			for (int i = 0; i < vehicle.trunk().getContainerSize(); i++) {
				items += vehicle.trunk().getItem(i).isEmpty() ? 0 : 1;
			}

			text.append("\n  trunk: ").append(items).append(" stacks");
		}

		source.sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx) {
		Vehicle vehicle = nearest(ctx.getSource());
		if (vehicle == null) {
			ctx.getSource().sendFailure(Component.literal("No vehicle within 8 blocks"));
			return 0;
		}

		float value = FloatArgumentType.getFloat(ctx, "value");
		switch (StringArgumentType.getString(ctx, "what")) {
			case "fuel" -> vehicle.setFuel(value);
			case "engine" -> vehicle.setEngine(Math.round(value));
			case "windows" -> vehicle.setWindows(Math.round(value));
			case "body" -> vehicle.setBody(Math.round(value));
			case "locked" -> vehicle.setLocked(value > 0.5F);
			case "battery" -> {
				ItemStack battery = vehicle.battery();
				if (!battery.isEmpty()) {
					CarBatteryItem.setCharge(battery, value);
					vehicle.parts().setChanged();
				}
			}
			default -> {
				ctx.getSource().sendFailure(Component.literal("Use fuel, engine, windows, body, battery or locked"));
				return 0;
			}
		}

		return info(ctx.getSource());
	}
}
