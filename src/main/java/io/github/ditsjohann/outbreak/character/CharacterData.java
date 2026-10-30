package io.github.ditsjohann.outbreak.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Who a player is in this life: name, profession, traits, skills, and a tally of the walkers put
 * down. A player without one ({@link #created} false) hasn't made their survivor yet. Mutable; the
 * server owns it and re-attaches a copy to sync it.
 */
public final class CharacterData {
	public static final Codec<CharacterData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("created", false).forGetter(c -> c.created),
		Codec.STRING.optionalFieldOf("name", "").forGetter(c -> c.name),
		Profession.CODEC.optionalFieldOf("profession", Profession.UNEMPLOYED).forGetter(c -> c.profession),
		Trait.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(c -> List.copyOf(c.traits)),
		Codec.unboundedMap(Skill.CODEC, Codec.FLOAT).optionalFieldOf("xp", Map.of()).forGetter(c -> c.xp),
		Codec.INT.optionalFieldOf("kills", 0).forGetter(c -> c.kills),
		Codec.LONG.optionalFieldOf("born", 0L).forGetter(c -> c.born)
	).apply(i, CharacterData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public boolean created;
	public String name;
	public Profession profession;
	public final EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);
	public final EnumMap<Skill, Float> xp = new EnumMap<>(Skill.class);
	public int kills;
	/** Overworld clock time when this survivor started out. */
	public long born;

	public CharacterData() {
		this(false, "", Profession.UNEMPLOYED, List.of(), Map.of(), 0, 0L);
	}

	private CharacterData(boolean created, String name, Profession profession, List<Trait> traits, Map<Skill, Float> xp, int kills, long born) {
		this.created = created;
		this.name = name;
		this.profession = profession;
		this.traits.addAll(traits);
		this.xp.putAll(xp);
		this.kills = kills;
		this.born = born;
	}

	/** A fresh survivor: the profession's skills and free traits, plus the picked traits. */
	public static CharacterData create(String name, Profession profession, Set<Trait> picked, long born) {
		CharacterData data = new CharacterData(true, name, profession, new ArrayList<>(picked), Map.of(), 0, born);
		data.traits.addAll(profession.traits);
		profession.skills.forEach((skill, level) -> data.xp.put(skill, Skill.xpFor(level)));
		return data;
	}

	public CharacterData copy() {
		return new CharacterData(this.created, this.name, this.profession, new ArrayList<>(this.traits), this.xp, this.kills, this.born);
	}

	public boolean has(Trait trait) {
		return this.traits.contains(trait);
	}

	public float xp(Skill skill) {
		return this.xp.getOrDefault(skill, 0.0F);
	}

	public int level(Skill skill) {
		return Skill.level(this.xp(skill));
	}

	/** Points left after picking these traits as this profession (must not go below 0). Free profession traits cost nothing. */
	public static int pointsLeft(Profession profession, Set<Trait> picked) {
		int points = profession.points;
		for (Trait trait : picked) {
			if (!profession.traits.contains(trait)) {
				points -= trait.cost;
			}
		}

		return points;
	}

	/** Whether this is a character anyone may make: a name, points not overspent, no opposites, only pickable traits. */
	public static boolean isValid(String name, Profession profession, Set<Trait> picked) {
		if (name.isBlank() || name.length() > 32 || pointsLeft(profession, picked) < 0) {
			return false;
		}

		for (Trait trait : picked) {
			Trait opposite = trait.opposite();
			if (!trait.isPickable() || (opposite != null && picked.contains(opposite))) {
				return false;
			}
		}

		return true;
	}
}
