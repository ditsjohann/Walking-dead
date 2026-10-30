package io.github.ditsjohann.outbreak.gore;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/**
 * The parts a walker can lose, by the name of their part in the walker model (hiding it hides
 * everything below it too: an arm takes its forearm with it). Losing the bit that's left after a
 * smaller loss (the rest of an arm after its forearm) is still possible.
 */
public enum Limb {
	RIGHT_FOREARM("right_forearm", null, new Vec3(-0.37, 1.0, 0.0)),
	LEFT_FOREARM("left_forearm", null, new Vec3(0.37, 1.0, 0.0)),
	RIGHT_ARM("right_arm", RIGHT_FOREARM, new Vec3(-0.37, 1.25, 0.0)),
	LEFT_ARM("left_arm", LEFT_FOREARM, new Vec3(0.37, 1.25, 0.0)),
	RIGHT_SHIN("right_shin", null, new Vec3(-0.12, 0.2, 0.0)),
	LEFT_SHIN("left_shin", null, new Vec3(0.12, 0.2, 0.0)),
	RIGHT_LEG("right_leg", RIGHT_SHIN, new Vec3(-0.12, 0.5, 0.0)),
	LEFT_LEG("left_leg", LEFT_SHIN, new Vec3(0.12, 0.5, 0.0)),
	HEAD("head", null, new Vec3(0.0, 1.75, 0.0));

	/** The walker model part that goes missing. */
	public final String part;
	/** The smaller loss inside this one (the forearm of an arm). */
	public final @Nullable Limb lower;
	/** Where it is on a standing walker, in blocks from its feet: x as in the model (negative is its right), y up, z forward. */
	public final Vec3 offset;

	Limb(String part, @Nullable Limb lower, Vec3 offset) {
		this.part = part;
		this.lower = lower;
		this.offset = offset;
	}

	public int bit() {
		return 1 << this.ordinal();
	}

	public boolean isLeg() {
		return this == RIGHT_SHIN || this == LEFT_SHIN || this == RIGHT_LEG || this == LEFT_LEG;
	}

	public boolean isArm() {
		return this == RIGHT_FOREARM || this == LEFT_FOREARM || this == RIGHT_ARM || this == LEFT_ARM;
	}

	/** Whether this part is gone, given the bits of everything lost. */
	public boolean isLost(int lost) {
		if ((lost & this.bit()) != 0) {
			return true;
		}

		// A forearm is gone with its arm.
		for (Limb limb : values()) {
			if (limb.lower == this && (lost & limb.bit()) != 0) {
				return true;
			}
		}

		return false;
	}

	public static Limb byOrdinal(int ordinal) {
		Limb[] values = values();
		return values[Math.floorMod(ordinal, values.length)];
	}
}
