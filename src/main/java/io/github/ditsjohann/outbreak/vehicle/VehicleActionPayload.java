package io.github.ditsjohann.outbreak.vehicle;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The driver does something: start or stop the engine, honk, click the ignition (value 1: take the key
 * out), or (sent by the client's physics) crash.
 */
public record VehicleActionPayload(int action, float value) implements CustomPacketPayload {
	public static final int ENGINE = 0;
	public static final int HORN = 1;
	public static final int CRASH = 2;
	public static final int IGNITION = 3;

	public static final CustomPacketPayload.Type<VehicleActionPayload> TYPE = new CustomPacketPayload.Type<>(Outbreak.id("vehicle_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VehicleActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, VehicleActionPayload::action,
		ByteBufCodecs.FLOAT, VehicleActionPayload::value,
		VehicleActionPayload::new);

	/** Set on the client: sends a payload to the server. Stays a no-op on a dedicated server. */
	public static java.util.function.Consumer<VehicleActionPayload> sender = payload -> {
	};

	@Override
	public CustomPacketPayload.Type<VehicleActionPayload> type() {
		return TYPE;
	}

	static void crash(float impact) {
		sender.accept(new VehicleActionPayload(CRASH, impact));
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!(player.getVehicle() instanceof Vehicle vehicle)) {
				return;
			}

			switch (payload.action) {
				case ENGINE -> vehicle.useIgnition(player, false);
				case IGNITION -> vehicle.useIgnition(player, payload.value > 0.0F);
				case HORN -> vehicle.honk(player);
				case CRASH -> vehicle.crash(player, payload.value);
				default -> {
				}
			}
		});
	}
}
