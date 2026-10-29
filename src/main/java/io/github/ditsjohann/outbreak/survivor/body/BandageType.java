package io.github.ditsjohann.outbreak.survivor.body;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum BandageType implements StringRepresentable {
	NONE("none", 0.5F, 0.0F),
	/** Torn cloth: stops the bleeding, but isn't sterile. */
	RAG("rag", 1.0F, 0.1F),
	/** Sterile bandage: heals faster and never infects. */
	BANDAGE("bandage", 1.5F, 0.0F);

	public static final Codec<BandageType> CODEC = StringRepresentable.fromEnum(BandageType::values);

	private final String name;
	private final float healSpeed;
	private final float infectionChance;

	BandageType(String name, float healSpeed, float infectionChance) {
		this.name = name;
		this.healSpeed = healSpeed;
		this.infectionChance = infectionChance;
	}

	public float healSpeed() {
		return this.healSpeed;
	}

	/** Chance that applying this to a wound that wasn't disinfected gives it a wound infection. */
	public float infectionChance() {
		return this.infectionChance;
	}

	public String translationKey() {
		return "outbreak.bandage." + this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
