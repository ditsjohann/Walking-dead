package io.github.ditsjohann.outbreak.survivor.body;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum BodyPart implements StringRepresentable {
	HEAD("head", 10),
	TORSO("torso", 25),
	LEFT_ARM("left_arm", 20),
	RIGHT_ARM("right_arm", 20),
	LEFT_LEG("left_leg", 12),
	RIGHT_LEG("right_leg", 13);

	public static final Codec<BodyPart> CODEC = StringRepresentable.fromEnum(BodyPart::values);

	private final String name;
	/** How likely a walker's attack lands on this part (out of 100). */
	private final int hitWeight;

	BodyPart(String name, int hitWeight) {
		this.name = name;
		this.hitWeight = hitWeight;
	}

	public int hitWeight() {
		return this.hitWeight;
	}

	public boolean isLeg() {
		return this == LEFT_LEG || this == RIGHT_LEG;
	}

	public String translationKey() {
		return "outbreak.body_part." + this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
