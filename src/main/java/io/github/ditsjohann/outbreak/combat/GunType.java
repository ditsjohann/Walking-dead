package io.github.ditsjohann.outbreak.combat;

import java.util.function.Supplier;

import io.github.ditsjohann.outbreak.item.OutbreakItems;
import io.github.ditsjohann.outbreak.world.OutbreakSounds;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * The guns, and what tells them apart. Damage is per bullet (per pellet for the shotgun) and goes
 * through the same rules as a melee hit: a body shot staggers but never kills, a head shot does four
 * times the damage. Spread is in degrees (from the hip, then aimed). Noise is how far walkers hear
 * the shot: every gun is loud, and a herd hears it a street away.
 */
public enum GunType {
	/** A police Glock: 9 mm, 15 in the magazine, quick. */
	PISTOL(15, () -> OutbreakItems.AMMO_9MM, 6.0F, 1, 2.5F, 0.6F, 6, 40, false, 64.0F, 64.0, 4.0F, () -> OutbreakSounds.PISTOL_SHOT),
	/** A pump shotgun: eight pellets, devastating close up, six shells loaded one at a time. */
	SHOTGUN(6, () -> OutbreakItems.SHOTGUN_SHELLS, 3.0F, 8, 6.0F, 4.5F, 18, 12, true, 80.0F, 28.0, 9.0F, () -> OutbreakSounds.SHOTGUN_SHOT),
	/** A bolt-action hunting rifle: five rounds, slow, dead accurate aimed, heard across the city. */
	RIFLE(5, () -> OutbreakItems.RIFLE_ROUNDS, 16.0F, 1, 3.5F, 0.1F, 26, 50, false, 112.0F, 160.0, 7.0F, () -> OutbreakSounds.RIFLE_SHOT);

	public final int capacity;
	private final Supplier<Item> ammo;
	public final float damage;
	public final int pellets;
	public final float hipSpread;
	public final float aimedSpread;
	/** Ticks between shots (the pump, the bolt). */
	public final int cooldown;
	/** Ticks to reload: the magazine, or each shell. */
	public final int reloadTicks;
	/** Loaded a round at a time (the shotgun's shells). */
	public final boolean oneAtATime;
	public final float noise;
	public final double range;
	/** Degrees the barrel kicks up. */
	public final float recoil;
	private final Supplier<Holder<SoundEvent>> sound;

	GunType(int capacity, Supplier<Item> ammo, float damage, int pellets, float hipSpread, float aimedSpread, int cooldown,
		int reloadTicks, boolean oneAtATime, float noise, double range, float recoil, Supplier<Holder<SoundEvent>> sound) {
		this.capacity = capacity;
		this.ammo = ammo;
		this.damage = damage;
		this.pellets = pellets;
		this.hipSpread = hipSpread;
		this.aimedSpread = aimedSpread;
		this.cooldown = cooldown;
		this.reloadTicks = reloadTicks;
		this.oneAtATime = oneAtATime;
		this.noise = noise;
		this.range = range;
		this.recoil = recoil;
		this.sound = sound;
	}

	public Item ammo() {
		return this.ammo.get();
	}

	public Holder<SoundEvent> sound() {
		return this.sound.get();
	}
}
