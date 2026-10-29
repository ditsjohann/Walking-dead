package io.github.ditsjohann.outbreak.survivor.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One wound on one body part. Mutable; only the server changes it. */
public final class Wound {
	/** A bandage needs changing after this long. */
	public static final int BANDAGE_DIRTY_TICKS = 20 * 60 * 3;
	/** A dirty bandage left on this long infects the wound. */
	public static final int BANDAGE_INFECTS_TICKS = BANDAGE_DIRTY_TICKS + 20 * 60 * 2;

	public static final Codec<Wound> CODEC = RecordCodecBuilder.create(i -> i.group(
		WoundType.CODEC.fieldOf("type").forGetter(w -> w.type),
		Codec.INT.fieldOf("heal_left").forGetter(w -> w.healLeft),
		Codec.INT.fieldOf("bleed_left").forGetter(w -> w.bleedLeft),
		Codec.BOOL.fieldOf("bleeding").forGetter(w -> w.bleeding),
		Codec.INT.optionalFieldOf("blood_timer", 0).forGetter(w -> w.bloodTimer),
		BandageType.CODEC.optionalFieldOf("bandage", BandageType.NONE).forGetter(w -> w.bandage),
		Codec.INT.optionalFieldOf("bandage_age", 0).forGetter(w -> w.bandageAge),
		Codec.BOOL.optionalFieldOf("infected", false).forGetter(w -> w.infected),
		Codec.BOOL.optionalFieldOf("disinfected", false).forGetter(w -> w.disinfected)
	).apply(i, Wound::new));

	public WoundType type;
	public int healLeft;
	/** Ticks of bleeding left; -1 = until bandaged. */
	public int bleedLeft;
	public boolean bleeding;
	public int bloodTimer;
	public BandageType bandage;
	public int bandageAge;
	/** A regular wound infection (from dirt), not the Knox infection. Curable with disinfectant. */
	public boolean infected;
	public boolean disinfected;

	public Wound(WoundType type, int healLeft, int bleedLeft, boolean bleeding, int bloodTimer, BandageType bandage, int bandageAge, boolean infected, boolean disinfected) {
		this.type = type;
		this.healLeft = healLeft;
		this.bleedLeft = bleedLeft;
		this.bleeding = bleeding;
		this.bloodTimer = bloodTimer;
		this.bandage = bandage;
		this.bandageAge = bandageAge;
		this.infected = infected;
		this.disinfected = disinfected;
	}

	public static Wound fresh(WoundType type) {
		return new Wound(type, type.healTicks(), type.bleedTicks(), true, 0, BandageType.NONE, 0, false, false);
	}

	public Wound copy() {
		return new Wound(this.type, this.healLeft, this.bleedLeft, this.bleeding, this.bloodTimer, this.bandage, this.bandageAge, this.infected, this.disinfected);
	}

	public boolean isBandaged() {
		return this.bandage != BandageType.NONE;
	}

	public boolean isBandageDirty() {
		return this.isBandaged() && this.bandageAge >= BANDAGE_DIRTY_TICKS;
	}

	public float pain() {
		return this.type.pain() + (this.infected ? 0.2F : 0.0F);
	}

	/** 0 = just happened, 1 = healed. */
	public float healProgress() {
		return 1.0F - (float) this.healLeft / this.type.healTicks();
	}
}
