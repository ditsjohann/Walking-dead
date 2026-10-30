package io.github.ditsjohann.outbreak.combat;

import io.github.ditsjohann.outbreak.item.OutbreakItems;
import io.github.ditsjohann.outbreak.vehicle.VehicleItems;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * How a weapon is swung, which decides both how it looks (first and third person) and what a hit
 * does to a walker: which way it's knocked, how hard, and how likely it goes down.
 * <p>
 * {@code lateral} is how far the blow comes across from the side, in degrees towards the attacker's
 * left: a bat swung right-handed sweeps right to left, so its hits throw walkers to your left.
 */
public enum WeaponStyle {
	/** Bare hands: a shove more than a punch. Pushes hard, does little. */
	FIST(0.0F, 0.55F, 0.12F, 0.0F),
	/** Knives and screwdrivers: quick thrusts, barely move a walker. */
	STAB(0.0F, 0.3F, 0.03F, 0.0F),
	/** Hammers, batons, machetes, crowbars, wrenches, axes: a diagonal chop. */
	CHOP(25.0F, 0.75F, 0.2F, 0.35F),
	/** The bat: a two-handed sweep. Sends them flying sideways. */
	SWING(75.0F, 1.0F, 0.35F, 0.5F),
	/** Anything else held in the hand. */
	OTHER(0.0F, 0.45F, 0.08F, 0.1F);

	public final float lateral;
	public final float strength;
	/** Chance a body hit knocks a walker down. */
	public final float knockdown;
	/** Chance a head hit that doesn't kill knocks a walker down. */
	public final float headKnockdown;

	WeaponStyle(float lateral, float strength, float knockdown, float headKnockdown) {
		this.lateral = lateral;
		this.strength = strength;
		this.knockdown = knockdown;
		this.headKnockdown = headKnockdown;
	}

	public static WeaponStyle of(ItemStack stack) {
		if (stack.isEmpty()) {
			return FIST;
		}

		if (stack.is(OutbreakItems.BASEBALL_BAT)) {
			return SWING;
		}

		if (stack.is(OutbreakItems.KITCHEN_KNIFE) || stack.is(VehicleItems.SCREWDRIVER)) {
			return STAB;
		}

		if (stack.is(OutbreakItems.HAMMER) || stack.is(OutbreakItems.BATON) || stack.is(OutbreakItems.MACHETE) || stack.is(OutbreakItems.CROWBAR)
			|| stack.is(VehicleItems.WRENCH) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SWORDS)) {
			return CHOP;
		}

		return OTHER;
	}
}
