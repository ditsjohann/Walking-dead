package io.github.ditsjohann.outbreak.survivor.body;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** Wound types, worst last. Times are in ticks. */
public enum WoundType implements StringRepresentable {
	/** Bleeds a little for 20 seconds, then stops on its own. 7% chance of the Knox infection. */
	SCRATCH("scratch", 0.07F, 20 * 20, 20 * 20, 20 * 60 * 3, 0.15F),
	/** Keeps bleeding until bandaged. 25% chance of the Knox infection. */
	LACERATION("laceration", 0.25F, -1, 20 * 12, 20 * 60 * 8, 0.3F),
	/** Keeps bleeding until bandaged. Always carries the Knox infection. */
	BITE("bite", 1.0F, -1, 20 * 8, 20 * 60 * 12, 0.45F);

	public static final Codec<WoundType> CODEC = StringRepresentable.fromEnum(WoundType::values);

	private final String name;
	private final float knoxChance;
	private final int bleedTicks;
	private final int ticksPerBloodLoss;
	private final int healTicks;
	private final float pain;

	WoundType(String name, float knoxChance, int bleedTicks, int ticksPerBloodLoss, int healTicks, float pain) {
		this.name = name;
		this.knoxChance = knoxChance;
		this.bleedTicks = bleedTicks;
		this.ticksPerBloodLoss = ticksPerBloodLoss;
		this.healTicks = healTicks;
		this.pain = pain;
	}

	public float knoxChance() {
		return this.knoxChance;
	}

	/** How long the wound bleeds when left alone; -1 means until it's bandaged. */
	public int bleedTicks() {
		return this.bleedTicks;
	}

	/** While bleeding, the survivor loses half a heart this often. */
	public int ticksPerBloodLoss() {
		return this.ticksPerBloodLoss;
	}

	public int healTicks() {
		return this.healTicks;
	}

	public float pain() {
		return this.pain;
	}

	public String translationKey() {
		return "outbreak.wound." + this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
