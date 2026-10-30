package io.github.ditsjohann.outbreak.world;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.item.RadioItem;
import io.github.ditsjohann.outbreak.noise.NoiseSystem;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * What the outside world still says, for the first week: NL-Alerts on everyone's phone at set times,
 * and the emergency broadcast on the radio (a portable radio switched on, or a car radio with the
 * engine running), a new message each day, repeated every few hours. Then a recorded message, and
 * then only static.
 */
public final class Broadcasts {
	/** An NL-Alert at an hour of a day (days since the outbreak began). */
	private record Alert(int day, int hour, String key) {
	}

	private static final List<Alert> ALERTS = List.of(
		new Alert(0, 8, "outbreak.alert.0"),
		new Alert(0, 15, "outbreak.alert.1"),
		new Alert(1, 8, "outbreak.alert.2"),
		new Alert(2, 9, "outbreak.alert.3"),
		new Alert(4, 6, "outbreak.alert.4"));
	/** The radio's message on each day (the last one on until the radio goes silent). */
	private static final String[] RADIO = {"outbreak.radio.0", "outbreak.radio.1", "outbreak.radio.2", "outbreak.radio.3",
		"outbreak.radio.4", "outbreak.radio.4", "outbreak.radio.5", "outbreak.radio.5", "outbreak.radio.5"};
	/** The radio repeats itself every three hours. */
	private static final long REPEAT = 3000L;

	private static long lastHour = -1;

	private Broadcasts() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(BroadcastPayload.TYPE, BroadcastPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> lastHour = Timeline.time(server) / 1000L);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		long time = Timeline.time(server);
		long hour = time / 1000L;
		if (hour != lastHour) {
			lastHour = hour;
			int day = Timeline.day(server);
			int clock = Timeline.hour(server);
			for (Alert alert : ALERTS) {
				if (alert.day == day && alert.hour == clock) {
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						alert(player, alert.key);
					}
				}
			}

			if (hour % (REPEAT / 1000L) == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (RadioItem.isListening(player)) {
						radio(player, current(server));
					}
				}
			}
		}
	}

	/** Today's message on the radio, or null once there's only static. */
	public static @Nullable String current(MinecraftServer server) {
		int day = Timeline.day(server);
		return day < RADIO.length ? RADIO[day] : null;
	}

	/** Someone just switched a radio on: static, then whatever is on. */
	public static void tuneIn(ServerPlayer player) {
		radio(player, current(player.level().getServer()));
	}

	/** An NL-Alert on this player's phone. */
	public static void alert(ServerPlayer player, String key) {
		ServerPlayNetworking.send(player, new BroadcastPayload(BroadcastPayload.ALERT, key));
		// Everyone's phone goes off at once: a herd hears a street full of them.
		NoiseSystem.emit(player.level(), player.position(), 8.0F, player);
	}

	/** A message on the radio (null: static). It's heard nearby too. */
	public static void radio(ServerPlayer player, @Nullable String key) {
		ServerPlayNetworking.send(player, new BroadcastPayload(BroadcastPayload.RADIO, key == null ? "" : key));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), OutbreakSounds.RADIO_STATIC, SoundSource.PLAYERS, 0.6F, 1.0F);
		NoiseSystem.emit(player.level(), player.position(), 6.0F, player);
	}

	/** To a client: an NL-Alert or a line on the radio (an empty key is static). */
	public record BroadcastPayload(int kind, String key) implements CustomPacketPayload {
		public static final int ALERT = 0;
		public static final int RADIO = 1;
		public static final CustomPacketPayload.Type<BroadcastPayload> TYPE = new CustomPacketPayload.Type<>(Outbreak.id("broadcast"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BroadcastPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BroadcastPayload::kind,
			ByteBufCodecs.STRING_UTF8, BroadcastPayload::key,
			BroadcastPayload::new);

		@Override
		public CustomPacketPayload.Type<BroadcastPayload> type() {
			return TYPE;
		}
	}
}
