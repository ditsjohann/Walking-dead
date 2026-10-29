package io.github.ditsjohann.outbreak.network;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.item.BagItem;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client asks to open the bag on its back. */
public record OpenBagPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenBagPayload> TYPE = new CustomPacketPayload.Type<>(Outbreak.id("open_bag"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBagPayload> CODEC = StreamCodec.unit(new OpenBagPayload());

	@Override
	public CustomPacketPayload.Type<OpenBagPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> BagItem.openWorn(context.player()));
	}
}
