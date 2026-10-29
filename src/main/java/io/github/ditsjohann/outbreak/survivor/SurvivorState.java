package io.github.ditsjohann.outbreak.survivor;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.ditsjohann.outbreak.survivor.body.BodyPart;
import io.github.ditsjohann.outbreak.survivor.body.Wound;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything about a survivor's body and needs: wounds per body part, the hidden Knox infection,
 * thirst, fatigue and panic. Needs go from 0 (fine) to 1 (critical). Mutable; the server owns it and
 * re-attaches a copy to sync it to the client.
 */
public final class SurvivorState {
	public static final Codec<SurvivorState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(BodyPart.CODEC, Wound.CODEC).optionalFieldOf("wounds", Map.of()).forGetter(s -> s.wounds),
		Codec.BOOL.optionalFieldOf("knox", false).forGetter(s -> s.knox),
		Codec.INT.optionalFieldOf("knox_ticks", 0).forGetter(s -> s.knoxTicks),
		Codec.INT.optionalFieldOf("poison_ticks", 0).forGetter(s -> s.poisonTicks),
		Codec.FLOAT.optionalFieldOf("thirst", 0.0F).forGetter(s -> s.thirst),
		Codec.FLOAT.optionalFieldOf("fatigue", 0.0F).forGetter(s -> s.fatigue),
		Codec.FLOAT.optionalFieldOf("panic", 0.0F).forGetter(s -> s.panic),
		Codec.FLOAT.optionalFieldOf("sickness", 0.0F).forGetter(s -> s.sickness)
	).apply(i, SurvivorState::new));

	/** What the client gets: everything except whether the sickness is really the Knox infection. */
	public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorState> CLIENT_STREAM_CODEC =
		ByteBufCodecs.fromCodecWithRegistries(CODEC).map(s -> s, SurvivorState::forClient);

	public final EnumMap<BodyPart, Wound> wounds = new EnumMap<>(BodyPart.class);
	/** Infected with the Knox infection. Hidden from the client: you only notice the symptoms. */
	public boolean knox;
	public int knoxTicks;
	/** Food poisoning from dirty water. Looks exactly like the start of the Knox infection. */
	public int poisonTicks;
	public float thirst;
	public float fatigue;
	public float panic;
	/** Visible sickness (0..1), worked out on the server from the Knox infection and food poisoning. */
	public float sickness;

	public SurvivorState() {
	}

	private SurvivorState(Map<BodyPart, Wound> wounds, boolean knox, int knoxTicks, int poisonTicks, float thirst, float fatigue, float panic, float sickness) {
		wounds.forEach((part, wound) -> this.wounds.put(part, wound.copy()));
		this.knox = knox;
		this.knoxTicks = knoxTicks;
		this.poisonTicks = poisonTicks;
		this.thirst = thirst;
		this.fatigue = fatigue;
		this.panic = panic;
		this.sickness = sickness;
	}

	public SurvivorState copy() {
		return new SurvivorState(this.wounds, this.knox, this.knoxTicks, this.poisonTicks, this.thirst, this.fatigue, this.panic, this.sickness);
	}

	private SurvivorState forClient() {
		return new SurvivorState(this.wounds, false, 0, 0, this.thirst, this.fatigue, this.panic, this.sickness);
	}

	public @Nullable Wound wound(BodyPart part) {
		return this.wounds.get(part);
	}

	public int bleedingCount() {
		int count = 0;
		for (Wound wound : this.wounds.values()) {
			if (wound.bleeding) {
				count++;
			}
		}

		return count;
	}

	public float pain() {
		float pain = 0.0F;
		for (Wound wound : this.wounds.values()) {
			pain += wound.pain();
		}

		return Math.min(pain, 1.0F);
	}
}
