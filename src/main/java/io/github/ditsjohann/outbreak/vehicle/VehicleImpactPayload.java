package io.github.ditsjohann.outbreak.vehicle;

import io.github.ditsjohann.outbreak.Outbreak;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Server to driver: you hit something, keep this much of your speed. */
public record VehicleImpactPayload(float keep) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VehicleImpactPayload> TYPE = new CustomPacketPayload.Type<>(Outbreak.id("vehicle_impact"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VehicleImpactPayload> CODEC =
		ByteBufCodecs.FLOAT.map(VehicleImpactPayload::new, VehicleImpactPayload::keep).cast();

	@Override
	public CustomPacketPayload.Type<VehicleImpactPayload> type() {
		return TYPE;
	}

	static void send(ServerPlayer player, float keep) {
		ServerPlayNetworking.send(player, new VehicleImpactPayload(keep));
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
