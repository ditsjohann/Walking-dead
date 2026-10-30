package io.github.ditsjohann.outbreak.character;

import java.util.Locale;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.StringRepresentable;

/**
 * Who you are, the good and the bad (PZ's traits). Good traits cost points, bad ones give points
 * back; the profession sets how many you start with. Opposites can't be taken together.
 */
public enum Trait implements StringRepresentable {
	// Good
	STRONG(6, "weak"),
	ATHLETIC(6, "out_of_shape"),
	FAST_LEARNER(4, "slow_learner"),
	LUCKY(4, "unlucky"),
	BRAVE(4, "cowardly"),
	THICK_SKINNED(6, "thin_skinned"),
	FAST_HEALER(4, "slow_healer"),
	LIGHT_FOOTED(3, "clumsy"),
	CAMEL(3, "thirsty"),
	INCONSPICUOUS(4, "conspicuous"),
	ORGANIZED(3, null),
	// Bad
	WEAK(-6, "strong"),
	OUT_OF_SHAPE(-4, "athletic"),
	SLOW_LEARNER(-4, "fast_learner"),
	UNLUCKY(-4, "lucky"),
	COWARDLY(-3, "brave"),
	THIN_SKINNED(-5, "thick_skinned"),
	SLOW_HEALER(-4, "fast_healer"),
	CLUMSY(-3, "light_footed"),
	THIRSTY(-3, "camel"),
	CONSPICUOUS(-4, "inconspicuous"),
	HEMOPHOBIC(-3, null),
	PRONE_TO_ILLNESS(-3, null),
	SHORT_SIGHTED(-2, null),
	// Only from a profession
	BURGLAR(0, null);

	public static final Codec<Trait> CODEC = StringRepresentable.fromEnum(Trait::values);

	/** Points it costs (negative: points it gives). */
	public final int cost;
	private final @Nullable String opposite;

	Trait(int cost, @Nullable String opposite) {
		this.cost = cost;
		this.opposite = opposite;
	}

	public boolean isPositive() {
		return this.cost > 0;
	}

	/** Can be picked on the character screen (not one that only comes with a profession). */
	public boolean isPickable() {
		return this != BURGLAR;
	}

	public @Nullable Trait opposite() {
		if (this.opposite == null) {
			return null;
		}

		return valueOf(this.opposite.toUpperCase(Locale.ROOT));
	}

	@Override
	public String getSerializedName() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "outbreak.trait." + this.getSerializedName();
	}

	public String descriptionKey() {
		return this.translationKey() + ".desc";
	}
}
