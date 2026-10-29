package io.github.ditsjohann.outbreak.vehicle;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ditsjohann.outbreak.Outbreak;

import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/** Item data for vehicle parts: fuel in a jerrycan, charge in a battery, and which vehicle a key opens. */
public final class VehicleComponents {
	/** Litres of fuel. */
	public static final DataComponentType<Float> FUEL = register("fuel",
		DataComponentType.<Float>builder().persistent(ExtraCodecs.NON_NEGATIVE_FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
	/** 0 (flat) to 1 (full). */
	public static final DataComponentType<Float> CHARGE = register("charge",
		DataComponentType.<Float>builder().persistent(Codec.floatRange(0.0F, 1.0F)).networkSynchronized(ByteBufCodecs.FLOAT).build());
	/** A picked-up bicycle remembers its colour. */
	public static final DataComponentType<Integer> VEHICLE_COLOR = register("vehicle_color",
		DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
	public static final DataComponentType<VehicleKey> VEHICLE_KEY = register("vehicle_key",
		DataComponentType.<VehicleKey>builder().persistent(VehicleKey.CODEC).networkSynchronized(VehicleKey.STREAM_CODEC).build());

	/** A key for one vehicle, with enough to describe it ("red hatchback"). */
	public record VehicleKey(UUID vehicle, String type, String color) {
		public static final Codec<VehicleKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("vehicle").forGetter(VehicleKey::vehicle),
			Codec.STRING.fieldOf("type").forGetter(VehicleKey::type),
			Codec.STRING.fieldOf("color").forGetter(VehicleKey::color)
		).apply(instance, VehicleKey::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, VehicleKey> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, VehicleKey::vehicle,
			ByteBufCodecs.STRING_UTF8, VehicleKey::type,
			ByteBufCodecs.STRING_UTF8, VehicleKey::color,
			VehicleKey::new);

		public Component describe() {
			return describe(this.type, this.color);
		}

		public static Component describe(String type, String color) {
			return Component.translatable("vehicle.outbreak.description",
				Component.translatable("vehicle.outbreak.color." + color), Component.translatable("entity.outbreak." + type));
		}
	}

	private VehicleComponents() {
	}

	private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Outbreak.id(name), type);
	}

	public static void register() {
	}
}
