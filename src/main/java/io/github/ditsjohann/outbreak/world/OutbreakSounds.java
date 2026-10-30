package io.github.ditsjohann.outbreak.world;

import io.github.ditsjohann.outbreak.Outbreak;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Our own sounds (made by tools/world/make_world.py). */
public final class OutbreakSounds {
	public static final Holder<SoundEvent> NL_ALERT = register("nl_alert");
	public static final Holder<SoundEvent> RADIO_STATIC = register("radio_static");
	public static final Holder<SoundEvent> FUEL_PUMP = register("fuel_pump");
	public static final Holder<SoundEvent> POWER_OUT = register("power_out");
	public static final Holder<SoundEvent> HELICOPTER = register("helicopter");
	public static final Holder<SoundEvent> GUNSHOT_DISTANT = register("gunshot_distant");
	public static final Holder<SoundEvent> PISTOL_SHOT = register("pistol_shot");
	public static final Holder<SoundEvent> SHOTGUN_SHOT = register("shotgun_shot");
	public static final Holder<SoundEvent> RIFLE_SHOT = register("rifle_shot");
	public static final Holder<SoundEvent> GUN_DRY = register("gun_dry");
	public static final Holder<SoundEvent> GUN_RELOAD = register("gun_reload");
	public static final Holder<SoundEvent> SHOTGUN_PUMP = register("shotgun_pump");

	private OutbreakSounds() {
	}

	private static Holder<SoundEvent> register(String name) {
		Identifier id = Outbreak.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Loads the class (so the sounds are registered). */
	public static void register() {
	}
}
