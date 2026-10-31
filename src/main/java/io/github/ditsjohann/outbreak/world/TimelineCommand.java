package io.github.ditsjohann.outbreak.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Operator commands for the outbreak's timeline:
 * <pre>
 * /outbreak timeline                   - the day, and when the water and power go
 * /outbreak timeline power|water on|off - cut it now, or bring it back for good
 * /outbreak timeline alert &lt;0-4&gt;       - send an NL-Alert to everyone
 * /outbreak timeline radio             - what you'd hear on a radio now
 * /outbreak timeline darken &lt;radius&gt;   - put the lights out in the chunks around you now
 * /outbreak place                      - what building you're in (Groningen's places, from OpenStreetMap)
 * </pre>
 */
final class TimelineCommand {
	private TimelineCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var timeline = Commands.literal("timeline")
			.executes(ctx -> info(ctx.getSource()))
			.then(Commands.literal("power")
				.then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), false, true)))
				.then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false, false))))
			.then(Commands.literal("water")
				.then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true, true)))
				.then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), true, false))))
			.then(Commands.literal("alert").then(Commands.argument("n", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
				for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
					Broadcasts.alert(player, "outbreak.alert." + IntegerArgumentType.getInteger(ctx, "n"));
				}

				return 1;
			})))
			.then(Commands.literal("radio").executes(ctx -> {
				Broadcasts.tuneIn(ctx.getSource().getPlayerOrException());
				return 1;
			}))
			.then(Commands.literal("darken").then(Commands.argument("radius", IntegerArgumentType.integer(0, 16)).executes(ctx -> {
				CommandSourceStack source = ctx.getSource();
				ServerLevel level = source.getLevel();
				int radius = IntegerArgumentType.getInteger(ctx, "radius");
				int cx = (int) Math.floor(source.getPosition().x) >> 4;
				int cz = (int) Math.floor(source.getPosition().z) >> 4;
				int lights = 0;
				for (int x = cx - radius; x <= cx + radius; x++) {
					for (int z = cz - radius; z <= cz + radius; z++) {
						LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
						if (chunk != null) {
							lights += PowerGrid.darken(level, chunk);
						}
					}
				}

				int total = lights;
				source.sendSuccess(() -> Component.literal("Put out " + total + " lights"), false);
				return total;
			})));
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(timeline)
			.then(Commands.literal("place").executes(ctx -> {
				// What building you're in, from the map of Groningen's places.
				net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
				Places.Kind kind = Places.at(pos);
				String text = (kind == null ? "No known building here" : "In " + name(kind)) + " (" + Places.count() + " places on this map)";
				ctx.getSource().sendSuccess(() -> Component.literal(text), false);
				return kind == null ? 0 : 1;
			})));
	}

	private static String name(Places.Kind kind) {
		return switch (kind) {
			case MILITARY -> "a military site";
			case FUEL -> "a petrol station";
			case CLOTHES -> "a clothes shop";
			case ELECTRONICS -> "an electronics shop";
			case BOOKS -> "a bookshop or library";
			case INDUSTRIAL -> "an industrial building";
			case OFFICE -> "an office";
			case POLICE -> "a police station";
			case STORE -> "a shop";
			default -> "a " + kind.id().replace('_', ' ');
		};
	}

	private static int set(CommandSourceStack source, boolean water, boolean on) {
		Timeline.get(source.getServer()).set(source.getServer(), water, on);
		return info(source);
	}

	private static int info(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		Timeline timeline = Timeline.get(server);
		String text = "Day " + (Timeline.day(server) + 1) + ", " + Timeline.hour(server) + ":00"
			+ "; water " + state(Timeline.isWaterOn(), timeline.waterOff())
			+ "; power " + state(Timeline.isPowerOn(), timeline.powerOff())
			+ "; radio " + (Broadcasts.current(server) == null ? "static" : Broadcasts.current(server));
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static String state(boolean on, long off) {
		if (!on) {
			return "off";
		}

		return off < 0 ? "on for good" : "on until day " + (off / Timeline.DAY + 1) + " " + ((off % Timeline.DAY) / 1000L + 6) % 24 + ":00";
	}
}
