package io.github.ditsjohann.outbreak.sandbox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ditsjohann.outbreak.Outbreak;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Where a world keeps its {@link SandboxSettings}, and how they reach everyone. The rules are chosen
 * when the world is first played: in singleplayer by the "New survivor" screens ({@link #pending}),
 * on a server they start as the default and an operator changes them with {@code /outbreak sandbox}.
 */
public final class Sandbox extends SavedData {
	private static final Codec<Sandbox> CODEC = RecordCodecBuilder.create(i -> i.group(
		SandboxSettings.CODEC.fieldOf("settings").forGetter(s -> s.settings),
		Codec.BOOL.optionalFieldOf("configured", false).forGetter(s -> s.configured)
	).apply(i, Sandbox::new));
	private static final SavedDataType<Sandbox> TYPE = new SavedDataType<>(Outbreak.id("sandbox"), Sandbox::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/**
	 * The rules in force. On the server, those of the running world; on a client, the ones the server
	 * sent. (In singleplayer both sides are the same game, and the same rules.)
	 */
	private static volatile SandboxSettings current = SandboxSettings.DEFAULT;
	/** Chosen on the "New survivor" screens for the next new world this game opens. */
	private static volatile @Nullable SandboxSettings pending;

	private SandboxSettings settings;
	private boolean configured;

	private Sandbox() {
		this(SandboxSettings.DEFAULT, false);
	}

	private Sandbox(SandboxSettings settings, boolean configured) {
		this.settings = settings;
		this.configured = configured;
	}

	public static SandboxSettings current() {
		return current;
	}

	/** Client side: what the server says the rules are. */
	public static void setFromServer(SandboxSettings settings) {
		current = settings;
	}

	/** Client side: use these rules for the next world that hasn't got any yet. */
	public static void setPending(@Nullable SandboxSettings settings) {
		pending = settings;
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenEditorPayload.TYPE, OpenEditorPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdatePayload.TYPE, UpdatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UpdatePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
				change(player.level().getServer(), payload.settings());
				player.sendSystemMessage(Component.translatable("outbreak.sandbox.saved"));
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(Sandbox::onStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = SandboxSettings.DEFAULT);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(new SyncPayload(current)));
		SandboxCommand.register();
	}

	private static void onStarted(MinecraftServer server) {
		Sandbox data = server.getDataStorage().computeIfAbsent(TYPE);
		if (!data.configured) {
			// A world played for the first time: the rules picked for it, or the default.
			SandboxSettings chosen = pending;
			data.settings = chosen != null ? chosen : SandboxSettings.DEFAULT;
			data.configured = true;
			data.setDirty();
			startClock(server, data.settings.scenario());
			Outbreak.LOGGER.info("New outbreak: {} ({}), day {}", data.settings.preset().getSerializedName(), data.settings.scenario().getSerializedName(), data.settings.scenario().startDay);
		}

		pending = null;
		current = data.settings;
	}

	/** The scenario's first morning: 07:00 on its start day. */
	private static void startClock(MinecraftServer server, SandboxSettings.Scenario scenario) {
		long ticks = (scenario.startDay - 1) * 24000L + 1000L;
		server.clockManager().setTotalTicks(server.registryAccess().getOrThrow(WorldClocks.OVERWORLD), ticks);
	}

	/** Save new rules for this world and tell everyone. */
	public static void change(MinecraftServer server, SandboxSettings settings) {
		Sandbox data = server.getDataStorage().computeIfAbsent(TYPE);
		data.settings = settings;
		data.configured = true;
		data.setDirty();
		current = settings;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new SyncPayload(settings));
		}
	}

	/** Server to client: the world's rules (on joining, and whenever they change). */
	public record SyncPayload(SandboxSettings settings) implements CustomPacketPayload {
		public static final Type<SyncPayload> TYPE = new Type<>(Outbreak.id("sandbox_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
			SandboxSettings.STREAM_CODEC.map(SyncPayload::new, SyncPayload::settings);

		@Override
		public Type<SyncPayload> type() {
			return TYPE;
		}
	}

	/** Server to an operator: open the sandbox settings screen. */
	public record OpenEditorPayload(SandboxSettings settings) implements CustomPacketPayload {
		public static final Type<OpenEditorPayload> TYPE = new Type<>(Outbreak.id("sandbox_open_editor"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditorPayload> CODEC =
			SandboxSettings.STREAM_CODEC.map(OpenEditorPayload::new, OpenEditorPayload::settings);

		@Override
		public Type<OpenEditorPayload> type() {
			return TYPE;
		}
	}

	/** Operator's client to server: save these rules. */
	public record UpdatePayload(SandboxSettings settings) implements CustomPacketPayload {
		public static final Type<UpdatePayload> TYPE = new Type<>(Outbreak.id("sandbox_update"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePayload> CODEC =
			SandboxSettings.STREAM_CODEC.map(UpdatePayload::new, UpdatePayload::settings);

		@Override
		public Type<UpdatePayload> type() {
			return TYPE;
		}
	}
}
