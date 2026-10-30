package io.github.ditsjohann.outbreak.character;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator commands for survivors:
 * <pre>
 * /outbreak character info &lt;player&gt;
 * /outbreak character create &lt;player&gt; &lt;profession&gt; [trait,trait,...]  - with their kit, where they stand
 * /outbreak character reset &lt;player&gt;                              - gone: they make a new one
 * /outbreak character xp &lt;player&gt; &lt;skill&gt; &lt;amount&gt;
 * </pre>
 */
final class CharacterCommand {
	private static final SimpleCommandExceptionType BAD = new SimpleCommandExceptionType(Component.translatable("outbreak.character.bad"));

	private CharacterCommand() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var info = Commands.literal("info").then(Commands.argument("player", EntityArgument.player())
			.executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))));
		var create = Commands.literal("create").then(Commands.argument("player", EntityArgument.player())
			.then(Commands.argument("profession", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Profession.values()).map(Profession::getSerializedName), builder))
				.executes(ctx -> create(ctx, ""))
				.then(Commands.argument("traits", StringArgumentType.greedyString())
					.executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "traits"))))));
		var reset = Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
			ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
			player.setAttached(Characters.DATA, new CharacterData());
			Characters.openCreation(player, null);
			ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + " has no survivor now"), true);
			return 1;
		}));
		var xp = Commands.literal("xp").then(Commands.argument("player", EntityArgument.player())
			.then(Commands.argument("skill", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Skill.values()).map(Skill::getSerializedName), builder))
				.then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F)).executes(ctx -> {
					ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
					Characters.addXp(player, skill(StringArgumentType.getString(ctx, "skill")), FloatArgumentType.getFloat(ctx, "amount"));
					return info(ctx.getSource(), player);
				}))));
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("character").then(info).then(create).then(reset).then(xp)));
	}

	private static int create(CommandContext<CommandSourceStack> ctx, String traitList) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Profession profession = Arrays.stream(Profession.values())
			.filter(p -> p.getSerializedName().equals(StringArgumentType.getString(ctx, "profession")))
			.findFirst().orElseThrow(BAD::create);
		Set<Trait> traits = EnumSet.noneOf(Trait.class);
		for (String name : traitList.split("[,\\s]+")) {
			if (!name.isEmpty()) {
				traits.add(Arrays.stream(Trait.values()).filter(t -> t.getSerializedName().equals(name)).findFirst().orElseThrow(BAD::create));
			}
		}

		player.getInventory().clearContent();
		Characters.start(player, CharacterData.create(player.getName().getString(), profession, traits, player.level().getOverworldClockTime()), null);
		return info(ctx.getSource(), player);
	}

	private static Skill skill(String name) throws CommandSyntaxException {
		return Arrays.stream(Skill.values()).filter(s -> s.getSerializedName().equals(name)).findFirst().orElseThrow(BAD::create);
	}

	private static int info(CommandSourceStack source, ServerPlayer player) {
		CharacterData data = Characters.get(player);
		if (!data.created) {
			source.sendSuccess(() -> Component.literal(player.getName().getString() + ": no survivor"), false);
			return 0;
		}

		String skills = Arrays.stream(Skill.values())
			.map(s -> String.format(Locale.ROOT, "%s %d (%.0f xp)", s.getSerializedName(), data.level(s), data.xp(s)))
			.collect(Collectors.joining(", "));
		String traits = data.traits.stream().map(Trait::getSerializedName).collect(Collectors.joining(", "));
		source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s: %s, %s, traits [%s], skills [%s], kills %d, capacity %.1f kg, days %d",
			player.getName().getString(), data.name, data.profession.getSerializedName(), traits, skills, data.kills, Perks.capacity(player),
			Characters.daysSurvived(player, data))), false);
		return 1;
	}
}
