package io.github.ditsjohann.outbreak.command;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ditsjohann.outbreak.inventory.CarryWeight;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;
import io.github.ditsjohann.outbreak.survivor.BodySimulation;
import io.github.ditsjohann.outbreak.survivor.Injuries;
import io.github.ditsjohann.outbreak.survivor.SurvivorState;
import io.github.ditsjohann.outbreak.survivor.Survivors;
import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;
import io.github.ditsjohann.outbreak.survivor.body.WoundType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * Admin and testing tools: {@code /outbreak status|wound|infect|cure|set|noise}. Shows the hidden Knox
 * infection, so it's for operators only.
 */
public final class OutbreakCommand {
	private OutbreakCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("noise").then(Commands.argument("pos", Vec3Argument.vec3())
				.then(Commands.argument("radius", FloatArgumentType.floatArg(1.0F, 128.0F)).executes(ctx -> {
					Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
					float radius = FloatArgumentType.getFloat(ctx, "radius");
					NoiseSystem.emit(ctx.getSource().getLevel(), pos, radius);
					ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Made a noise of %.0f blocks", radius)), false);
					return 1;
				}))))
			.then(Commands.literal("status").then(player().executes(ctx -> status(ctx.getSource(), target(ctx)))))
			.then(Commands.literal("wound").then(player()
				.then(enumArgument("part", BodyPart.values())
					.then(enumArgument("type", WoundType.values()).executes(ctx -> {
						ServerPlayer player = target(ctx);
						Injuries.inflict(player, parse(ctx, "part", BodyPart.values()), parse(ctx, "type", WoundType.values()));
						return status(ctx.getSource(), player);
					})))))
			.then(Commands.literal("infect").then(player()
				.executes(ctx -> infect(ctx.getSource(), target(ctx), 0.0F))
				.then(Commands.argument("progress", FloatArgumentType.floatArg(0.0F, 0.99F))
					.executes(ctx -> infect(ctx.getSource(), target(ctx), FloatArgumentType.getFloat(ctx, "progress"))))))
			.then(Commands.literal("cure").then(player().executes(ctx -> {
				ServerPlayer player = target(ctx);
				player.setAttached(Survivors.STATE, new SurvivorState());
				return status(ctx.getSource(), player);
			})))
			.then(Commands.literal("set").then(player()
				.then(Commands.argument("need", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[] {"thirst", "fatigue", "panic"}, builder))
					.then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F)).executes(ctx -> {
						ServerPlayer player = target(ctx);
						SurvivorState state = Survivors.get(player);
						float value = FloatArgumentType.getFloat(ctx, "value");
						switch (StringArgumentType.getString(ctx, "need")) {
							case "thirst" -> state.thirst = value;
							case "fatigue" -> state.fatigue = value;
							case "panic" -> state.panic = value;
							default -> {
								ctx.getSource().sendFailure(Component.literal("Unknown need; use thirst, fatigue or panic"));
								return 0;
							}
						}

						Survivors.sync(player);
						return status(ctx.getSource(), player);
					}))))));
	}

	private static int infect(CommandSourceStack source, ServerPlayer player, float progress) {
		SurvivorState state = Survivors.get(player);
		state.knox = true;
		state.knoxTicks = (int) (progress * BodySimulation.KNOX_TICKS);
		Survivors.sync(player);
		return status(source, player);
	}

	private static int status(CommandSourceStack source, ServerPlayer player) {
		SurvivorState state = Survivors.get(player);
		StringBuilder text = new StringBuilder(player.getName().getString())
			.append(String.format(Locale.ROOT, ": health %.1f, thirst %.2f, fatigue %.2f, panic %.2f, sickness %.2f",
				player.getHealth(), state.thirst, state.fatigue, state.panic, state.sickness))
			.append(state.knox ? String.format(Locale.ROOT, ", KNOX %.0f%%", BodySimulation.knoxProgress(state) * 100) : ", no knox")
			.append(String.format(Locale.ROOT, ", carrying %.1f/%.0f kg", CarryWeight.total(player), CarryWeight.CAPACITY));
		state.wounds.forEach((part, wound) -> text.append("\n  ").append(part.getSerializedName()).append(": ").append(describe(wound)));
		source.sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static String describe(Wound wound) {
		return String.format(Locale.ROOT, "%s%s, bandage %s%s%s, healed %.0f%%",
			wound.type.getSerializedName(),
			wound.bleeding ? " (bleeding)" : "",
			wound.bandage.getSerializedName(),
			wound.isBandageDirty() ? " (dirty)" : "",
			wound.infected ? ", INFECTED" : "",
			wound.healProgress() * 100);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
		return Commands.argument("player", EntityArgument.player());
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, "player");
	}

	private static <E extends Enum<E> & StringRepresentable> RequiredArgumentBuilder<CommandSourceStack, String> enumArgument(String name, E[] values) {
		return Commands.argument(name, StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(values).map(StringRepresentable::getSerializedName), builder));
	}

	private static <E extends Enum<E> & StringRepresentable> E parse(CommandContext<CommandSourceStack> ctx, String name, E[] values) throws CommandSyntaxException {
		String value = StringArgumentType.getString(ctx, name);
		for (E e : values) {
			if (e.getSerializedName().equals(value)) {
				return e;
			}
		}

		throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown " + name + ": " + value)).create();
	}
}
