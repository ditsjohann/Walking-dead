package io.github.ditsjohann.outbreak.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.gore.Blood;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Walker guts smeared all over you (TWD): the dead take you for one of them and walk right past, as
 * long as you walk too. Rain or water washes it off, running shakes it off, and the moment you attack
 * one of them the others know. It wears off in a quarter of an hour anyway.
 */
public final class Camouflage {
	/** The game time it wears off at; synced to everyone (so everyone sees you covered). */
	public static final AttachmentType<Long> GUTS = AttachmentRegistry.create(Outbreak.id("guts"), builder -> builder
		.persistent(Codec.LONG)
		.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	private static final long DURATION = 20L * 60 * 15;
	/** Ticks of running it takes to shake it off. */
	private static final int SPRINT_LIMIT = 60;
	private static final int CHECK = 10;

	private static final Map<UUID, Integer> SPRINTING = new HashMap<>();

	private Camouflage() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.hasAttached(GUTS)) {
					continue;
				}

				if (!isCovered(player)) {
					wash(player, "outbreak.guts.worn_off");
				} else if (player.isInWaterOrRain()) {
					wash(player, "outbreak.guts.washed");
				} else if (player.isSprinting()) {
					int ticks = SPRINTING.merge(player.getUUID(), CHECK, Integer::sum);
					if (ticks >= SPRINT_LIMIT) {
						wash(player, "outbreak.guts.ran");
					}
				} else {
					SPRINTING.remove(player.getUUID());
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPRINTING.remove(handler.player.getUUID()));
	}

	public static boolean isCovered(Player player) {
		Long until = player.getAttached(GUTS);
		return until != null && until > player.level().getGameTime();
	}

	/** Smear the guts on. */
	public static void apply(ServerPlayer player) {
		player.setAttached(GUTS, player.level().getGameTime() + DURATION);
		SPRINTING.remove(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.6F);
		Blood.splat(player.level(), player.position(), 1);
		player.sendOverlayMessage(Component.translatable("outbreak.guts.applied"));
	}

	/** It comes off (key: why, as a message). */
	public static void wash(ServerPlayer player, String key) {
		player.removeAttached(GUTS);
		SPRINTING.remove(player.getUUID());
		player.sendOverlayMessage(Component.translatable(key));
	}
}
