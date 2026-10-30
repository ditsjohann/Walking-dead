package io.github.ditsjohann.outbreak.client.world;

import io.github.ditsjohann.outbreak.world.Helicopter;
import io.github.ditsjohann.outbreak.world.OutbreakSounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;

/** The rotors' thwop, following the helicopter (heard a long way off: see sounds.json). */
public class HelicopterSound extends AbstractTickableSoundInstance {
	private final Helicopter helicopter;

	public HelicopterSound(Helicopter helicopter) {
		super(OutbreakSounds.HELICOPTER.value(), SoundSource.AMBIENT, helicopter.getRandom());
		this.helicopter = helicopter;
		this.looping = true;
		this.delay = 0;
		this.volume = 1.0F;
		this.x = helicopter.getX();
		this.y = helicopter.getY();
		this.z = helicopter.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (this.helicopter.isRemoved()) {
			this.stop();
			return;
		}

		this.x = this.helicopter.getX();
		this.y = this.helicopter.getY();
		this.z = this.helicopter.getZ();
	}
}
