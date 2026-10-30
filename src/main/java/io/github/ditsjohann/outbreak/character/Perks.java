package io.github.ditsjohann.outbreak.character;

import net.minecraft.world.entity.player.Player;

/**
 * What a survivor's skills and traits do, as numbers the rest of the game asks for. All of these
 * work on both sides (the character is synced to its player), and give the plain value for
 * someone without a character.
 */
public final class Perks {
	private Perks() {
	}

	private static CharacterData data(Player player) {
		return Characters.get(player);
	}

	/** How much you can carry before you're overloaded (kg). */
	public static float capacity(Player player) {
		CharacterData data = data(player);
		float capacity = 12.0F + 0.5F * data.level(Skill.STRENGTH);
		if (data.has(Trait.STRONG)) {
			capacity += 4.0F;
		} else if (data.has(Trait.WEAK)) {
			capacity -= 4.0F;
		}

		return Math.max(1.0F, capacity);
	}

	/** Extra weight a worn bag takes off (on top of the bag's own). */
	public static float bagBonus(Player player) {
		return data(player).has(Trait.ORGANIZED) ? 0.15F : 0.0F;
	}

	public static float fatigueMultiplier(Player player) {
		CharacterData data = data(player);
		float multiplier = 1.0F - 0.04F * data.level(Skill.FITNESS);
		return multiplier * (data.has(Trait.ATHLETIC) ? 0.6F : data.has(Trait.OUT_OF_SHAPE) ? 1.4F : 1.0F);
	}

	public static float thirstMultiplier(Player player) {
		CharacterData data = data(player);
		return data.has(Trait.CAMEL) ? 0.7F : data.has(Trait.THIRSTY) ? 1.4F : 1.0F;
	}

	/** Walking speed: athletes are a little quicker, the out of shape a little slower. */
	public static float speedBonus(Player player) {
		CharacterData data = data(player);
		return data.has(Trait.ATHLETIC) ? 0.05F : data.has(Trait.OUT_OF_SHAPE) ? -0.05F : 0.0F;
	}

	public static float xpMultiplier(CharacterData data) {
		return data.has(Trait.FAST_LEARNER) ? 1.3F : data.has(Trait.SLOW_LEARNER) ? 0.7F : 1.0F;
	}

	/** How much loot you find (the sandbox setting multiplies on top). */
	public static double lootLuck(Player player) {
		CharacterData data = data(player);
		return data.has(Trait.LUCKY) ? 1.25 : data.has(Trait.UNLUCKY) ? 0.75 : 1.0;
	}

	/** How quickly panic builds up. */
	public static float panicMultiplier(Player player) {
		CharacterData data = data(player);
		return data.has(Trait.BRAVE) ? 0.5F : data.has(Trait.COWARDLY) ? 1.6F : 1.0F;
	}

	/** How quickly wounds heal and health comes back. */
	public static float healMultiplier(Player player) {
		CharacterData data = data(player);
		return data.has(Trait.FAST_HEALER) ? 1.4F : data.has(Trait.SLOW_HEALER) ? 0.7F : 1.0F;
	}

	/** Bandaged wounds heal faster with first aid. */
	public static float bandagedHealMultiplier(Player player) {
		return 1.0F + 0.06F * data(player).level(Skill.FIRST_AID);
	}

	/** How likely a bandage or a dirty wound is to get infected. */
	public static float woundInfectionMultiplier(Player player) {
		CharacterData data = data(player);
		float multiplier = Math.max(0.2F, 1.0F - 0.08F * data.level(Skill.FIRST_AID));
		return multiplier * (data.has(Trait.PRONE_TO_ILLNESS) ? 2.0F : 1.0F);
	}

	/** How far your footsteps carry. */
	public static float stepNoise(Player player) {
		CharacterData data = data(player);
		float multiplier = Math.max(0.3F, 1.0F - 0.06F * data.level(Skill.SNEAKING));
		return multiplier * (data.has(Trait.LIGHT_FOOTED) ? 0.6F : data.has(Trait.CLUMSY) ? 1.5F : 1.0F);
	}

	/** How far walkers can see you. */
	public static float visibility(Player player) {
		CharacterData data = data(player);
		float multiplier = data.has(Trait.INCONSPICUOUS) ? 0.75F : data.has(Trait.CONSPICUOUS) ? 1.3F : 1.0F;
		if (player.isCrouching()) {
			multiplier *= Math.max(0.5F, 1.0F - 0.04F * data.level(Skill.SNEAKING));
		}

		return multiplier;
	}

	/** Melee damage. */
	public static float meleeDamage(Player player) {
		return 1.0F + 0.05F * data(player).level(Skill.FIGHTING);
	}

	/** How hard your hits push a walker. */
	public static float push(Player player) {
		CharacterData data = data(player);
		float multiplier = 1.0F + 0.03F * data.level(Skill.STRENGTH);
		return multiplier * (data.has(Trait.STRONG) ? 1.3F : data.has(Trait.WEAK) ? 0.8F : 1.0F);
	}

	/** Chance a hit doesn't wear your weapon. */
	public static float sparedWear(Player player) {
		return 0.06F * data(player).level(Skill.MAINTENANCE);
	}

	/** Chance a plank goes up without using up the plank and nails. */
	public static float sparedMaterials(Player player) {
		return 0.07F * data(player).level(Skill.CARPENTRY);
	}

	public static float hotwireChance(Player player) {
		CharacterData data = data(player);
		float chance = 0.4F + 0.05F * data.level(Skill.MECHANICS);
		if (data.has(Trait.BURGLAR)) {
			chance += 0.25F;
		}

		chance += data.has(Trait.LUCKY) ? 0.05F : data.has(Trait.UNLUCKY) ? -0.05F : 0.0F;
		return Math.min(0.95F, chance);
	}

	/** Burglars can hotwire with their bare hands. */
	public static boolean hotwiresBareHanded(Player player) {
		return data(player).has(Trait.BURGLAR);
	}

	/** Extra engine condition per repair. */
	public static int engineRepairBonus(Player player) {
		return 2 * data(player).level(Skill.MECHANICS);
	}

	/** Extra window condition per repair. */
	public static int windowRepairBonus(Player player) {
		return 5 * data(player).level(Skill.MECHANICS);
	}

	/** Chance a walker hit leaves no wound at all (thick skin). */
	public static float woundDodge(Player player) {
		return data(player).has(Trait.THICK_SKINNED) ? 0.25F : 0.0F;
	}

	/** Chance a walker hit leaves a worse wound than it would have (thin skin). */
	public static float woundWorsen(Player player) {
		return data(player).has(Trait.THIN_SKINNED) ? 0.25F : 0.0F;
	}

	public static boolean isShortSighted(Player player) {
		return data(player).has(Trait.SHORT_SIGHTED);
	}

	public static boolean isHemophobic(Player player) {
		return data(player).has(Trait.HEMOPHOBIC);
	}
}
