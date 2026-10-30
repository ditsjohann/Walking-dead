package io.github.ditsjohann.outbreak.sandbox;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator commands for the world's sandbox settings:
 * <pre>
 * /outbreak sandbox                         - open the settings screen
 * /outbreak sandbox info                    - print them
 * /outbreak sandbox preset &lt;preset&gt;
 * /outbreak sandbox scenario &lt;scenario&gt;     - (doesn't move the clock)
 * /outbreak sandbox set &lt;setting&gt; &lt;value&gt;
 * </pre>
 */
final class SandboxCommand {
	private static final SimpleCommandExceptionType BAD_VALUE = new SimpleCommandExceptionType(Component.translatable("outbreak.sandbox.bad_value"));

	private SandboxCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var preset = Commands.literal("preset").then(Commands.argument("preset", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(SandboxSettings.Preset.values()).map(SandboxSettings.Preset::getSerializedName), builder))
			.executes(ctx -> change(ctx, Sandbox.current().withPreset(parse(SandboxSettings.Preset.values(), StringArgumentType.getString(ctx, "preset"))))));
		var scenario = Commands.literal("scenario").then(Commands.argument("scenario", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(SandboxSettings.Scenario.values()).map(SandboxSettings.Scenario::getSerializedName), builder))
			.executes(ctx -> change(ctx, Sandbox.current().withScenario(parse(SandboxSettings.Scenario.values(), StringArgumentType.getString(ctx, "scenario"))))));
		var set = Commands.literal("set").then(Commands.argument("setting", StringArgumentType.word())
			.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SandboxSettings.KEYS, builder))
			.then(Commands.argument("value", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SandboxSettings.values(StringArgumentType.getString(ctx, "setting")), builder))
				.executes(SandboxCommand::set)));
		var sandbox = Commands.literal("sandbox")
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ServerPlayNetworking.send(player, new Sandbox.OpenEditorPayload(Sandbox.current()));
				return 1;
			})
			.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
			.then(preset)
			.then(scenario)
			.then(set);
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(sandbox));
	}

	private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		try {
			return change(ctx, Sandbox.current().with(StringArgumentType.getString(ctx, "setting"), StringArgumentType.getString(ctx, "value")));
		} catch (IllegalArgumentException e) {
			throw BAD_VALUE.create();
		}
	}

	private static int change(CommandContext<CommandSourceStack> ctx, SandboxSettings settings) {
		Sandbox.change(ctx.getSource().getServer(), settings);
		return info(ctx.getSource());
	}

	private static int info(CommandSourceStack source) {
		SandboxSettings s = Sandbox.current();
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
			"sandbox: %s, scenario %s (day %d), map %s [%s], walker_speed %s, population %s (x%.2f), herd_size %s, infection %s, loot %s (x%.2f), permadeath %s, vanilla_monsters %s",
			s.preset().getSerializedName(), s.scenario().getSerializedName(), s.scenario().startDay, s.map().getSerializedName(), s.bbox(),
			s.walkerSpeed().getSerializedName(), s.population().getSerializedName(), s.populationMultiplier(), s.herdSize().getSerializedName(),
			s.infection().getSerializedName(), s.loot().getSerializedName(), s.lootMultiplier(), s.permadeath(), s.vanillaMonsters())), false);
		return 1;
	}

	private static <E extends Enum<E> & net.minecraft.util.StringRepresentable> E parse(E[] values, String name) throws CommandSyntaxException {
		for (E value : values) {
			if (value.getSerializedName().equals(name)) {
				return value;
			}
		}

		throw BAD_VALUE.create();
	}
}
