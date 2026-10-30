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
