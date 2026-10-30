package io.github.ditsjohann.outbreak.character;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Skills level up by doing (0 to 10). Each level needs a bit more practice than the last: level 1
 * at 75 xp, level 5 at 1125, level 10 at 4125.
 */
public enum Skill implements StringRepresentable {
	/** Hitting walkers. +5% melee damage a level. */
	FIGHTING,
	/** Looking after your weapons. 6% a level that a hit doesn't wear the weapon. */
	MAINTENANCE,
	/** Nailing planks. 7% a level that a plank goes up without using up the plank and nails. */
	CARPENTRY,
	/** Treating wounds. Bandaged wounds heal 6% faster a level; bandages infect 8% less a level. */
	FIRST_AID,
	/** Cars. +5% a level to hotwire; repairs fix more. */
	MECHANICS,
	/** Moving unheard. Footsteps 6% quieter a level; walkers see you crouching 4% less far a level. */
	SNEAKING,
	/** Running. You tire 4% slower a level. */
	FITNESS,
	/** Carrying. +0.5 kg capacity a level, and your hits push harder. */
	STRENGTH;

	public static final Codec<Skill> CODEC = StringRepresentable.fromEnum(Skill::values);
	public static final int MAX_LEVEL = 10;

	/** Total xp needed to reach a level. */
	public static float xpFor(int level) {
		return 75.0F * level * (level + 1) / 2.0F;
	}

	public static int level(float xp) {
		int level = 0;
		while (level < MAX_LEVEL && xp >= xpFor(level + 1)) {
			level++;
		}

		return level;
	}

	/** How far towards the next level (0..1); 1 at the top. */
	public static float progress(float xp) {
		int level = level(xp);
		if (level >= MAX_LEVEL) {
			return 1.0F;
		}

		return (xp - xpFor(level)) / (xpFor(level + 1) - xpFor(level));
	}

	@Override
	public String getSerializedName() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "outbreak.skill." + this.getSerializedName();
	}
}
