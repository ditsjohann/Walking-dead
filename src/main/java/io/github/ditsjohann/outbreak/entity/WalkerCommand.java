package io.github.ditsjohann.outbreak.entity;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.ditsjohann.outbreak.gore.Limb;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Operator commands to show off (and test) walker physics without a fight:
 * <pre>
 * /outbreak walker hit &lt;walkers&gt; &lt;yaw&gt; &lt;strength 0-2&gt; [head]  - a hit pushing towards yaw
 * /outbreak walker knockdown &lt;walkers&gt; &lt;yaw&gt;                - falls over towards yaw
 * /outbreak walker stand &lt;walkers&gt;                         - back on its feet at once
 * /outbreak walker sever &lt;walkers&gt; &lt;limb&gt;                     - right_forearm, left_arm, right_leg, head...
 * /outbreak walker crawler &lt;walkers&gt; &lt;true|false&gt;
 * /outbreak walker skin &lt;walkers&gt; &lt;0-23&gt;
 * /outbreak walker info &lt;walkers&gt;
 * /outbreak walker corpses &lt;radius&gt;                       - dead walkers still lying around (selectors skip them)
 * </pre>
 */
public final class WalkerCommand {
	private WalkerCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var hit = Commands.literal("hit").then(walkers()
			.then(Commands.argument("yaw", FloatArgumentType.floatArg())
				.then(Commands.argument("strength", FloatArgumentType.floatArg(0.0F, 2.0F))
					.executes(ctx -> hit(ctx, false))
					.then(Commands.argument("head", BoolArgumentType.bool()).executes(ctx -> hit(ctx, BoolArgumentType.getBool(ctx, "head")))))));
		var knockdown = Commands.literal("knockdown").then(walkers()
			.then(Commands.argument("yaw", FloatArgumentType.floatArg())
				.executes(ctx -> each(ctx, "knocked down", walker -> walker.knockDown(FloatArgumentType.getFloat(ctx, "yaw"), 80)))));
		var stand = Commands.literal("stand").then(walkers().executes(ctx -> each(ctx, "stood up", Walker::standUp)));
		var sever = Commands.literal("sever").then(walkers()
			.then(Commands.argument("limb", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Limb.values()).map(l -> l.name().toLowerCase(Locale.ROOT)), builder))
				.executes(ctx -> {
					Limb limb = Limb.valueOf(StringArgumentType.getString(ctx, "limb").toUpperCase(Locale.ROOT));
					return each(ctx, "cut", walker -> walker.sever((ServerLevel) walker.level(), limb, walker.getLookAngle().scale(-1.0).add(0.0, 0.3, 0.0)));
				})));
		var crawler = Commands.literal("crawler").then(walkers()
			.then(Commands.argument("crawler", BoolArgumentType.bool())
				.executes(ctx -> each(ctx, "changed", walker -> walker.setCrawler(BoolArgumentType.getBool(ctx, "crawler"))))));
		var skin = Commands.literal("skin").then(walkers()
			.then(Commands.argument("skin", IntegerArgumentType.integer(0, Walker.SKINS - 1))
				.executes(ctx -> each(ctx, "reskinned", walker -> walker.setSkin(IntegerArgumentType.getInteger(ctx, "skin"))))));
		var info = Commands.literal("info").then(walkers().executes(ctx -> each(ctx, "described", walker ->
			ctx.getSource().sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
				"walker: skin %d, %s, %s, health %.1f%s, at %.1f %.1f %.1f", walker.skin(), walker.isCrawler() ? "crawler" : "walking",
				walker.posture().name().toLowerCase(java.util.Locale.ROOT), walker.getHealth(), walker.isDeadOrDying() ? " (DEAD)" : "",
				walker.getX(), walker.getY(), walker.getZ())), false))));
		var corpses = Commands.literal("corpses").then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
			.executes(ctx -> corpses(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))));
		var census = Commands.literal("census").then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
			.executes(ctx -> census(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))));
		dispatcher.register(Commands.literal("outbreak")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("walker").then(hit).then(knockdown).then(stand).then(sever).then(crawler).then(skin).then(info).then(corpses)
				.then(census)));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, ?> walkers() {
		return Commands.argument("walkers", EntityArgument.entities());
	}

	/** How many walkers are about: by distance, how many lead a herd, how many are in the open. */
	private static int census(CommandSourceStack source, int radius) {
		AABB area = AABB.ofSize(source.getPosition(), radius * 2, 512, radius * 2);
		int[] bands = new int[4];
		int leaders = 0;
		int outside = 0;
		int total = 0;
		for (Walker walker : source.getLevel().getEntitiesOfClass(Walker.class, area, Walker::isAlive)) {
			double distance = Math.sqrt(walker.distanceToSqr(source.getPosition()));
			if (distance > radius) {
				continue;
			}

			total++;
			bands[distance < 24 ? 0 : distance < 48 ? 1 : distance < 96 ? 2 : 3]++;
			if (walker.getLeader() == null) {
				leaders++;
			}

			if (walker.level().canSeeSky(walker.blockPosition())) {
				outside++;
			}
		}

		String text = String.format(java.util.Locale.ROOT, "%d walkers within %d blocks (<24: %d, 24-48: %d, 48-96: %d, 96+: %d), %d herds/loners, %d in the open",
			total, radius, bands[0], bands[1], bands[2], bands[3], leaders, outside);
		source.sendSuccess(() -> Component.literal(text), false);
		return total;
	}

	private static int corpses(CommandSourceStack source, int radius) {
		AABB area = AABB.ofSize(source.getPosition(), radius * 2, radius * 2, radius * 2);
		int count = source.getLevel().getEntitiesOfClass(Walker.class, area, Walker::isDeadOrDying).size();
		source.sendSuccess(() -> Component.literal(count + " walker corpse(s) within " + radius + " blocks"), false);
		return count;
	}

	private static int hit(CommandContext<CommandSourceStack> ctx, boolean head) throws CommandSyntaxException {
		float yaw = FloatArgumentType.getFloat(ctx, "yaw");
		float strength = FloatArgumentType.getFloat(ctx, "strength");
		return each(ctx, "hit", walker -> walker.react(yaw, strength, head));
	}

	private static int each(CommandContext<CommandSourceStack> ctx, String what, java.util.function.Consumer<Walker> action) throws CommandSyntaxException {
		int count = 0;
		for (Entity entity : EntityArgument.getEntities(ctx, "walkers")) {
			if (entity instanceof Walker walker) {
				action.accept(walker);
				count++;
			}
		}

		int done = count;
		ctx.getSource().sendSuccess(() -> Component.literal(done + " walker(s) " + what), false);
		return done;
	}
}
