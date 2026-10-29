package io.github.ditsjohann.outbreak.client.vehicle;

import io.github.ditsjohann.outbreak.vehicle.Vehicle;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** A running engine: a low rumble (the minecart's, tuned down) that rises with speed. */
public class EngineSound extends AbstractTickableSoundInstance {
	private final Vehicle vehicle;

	public EngineSound(Vehicle vehicle) {
		super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, vehicle.getRandom());
		this.vehicle = vehicle;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.6F;
		this.pitch = 0.4F;
		this.x = vehicle.getX();
		this.y = vehicle.getY();
		this.z = vehicle.getZ();
	}

	public Vehicle vehicle() {
		return this.vehicle;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (this.vehicle.isRemoved() || !this.vehicle.isRunning()) {
			this.stop();
			return;
		}

		this.x = this.vehicle.getX();
		this.y = this.vehicle.getY();
		this.z = this.vehicle.getZ();
		float moving = (float) Math.hypot(this.vehicle.getX() - this.vehicle.xo, this.vehicle.getZ() - this.vehicle.zo);
		float load = Mth.clamp(moving / this.vehicle.vehicleType().maxSpeed, 0.0F, 1.0F);
		this.pitch = Mth.lerp(0.2F, this.pitch, 0.4F + load * 0.9F);
		this.volume = Mth.lerp(0.2F, this.volume, 0.5F + load * 0.5F);
	}
}
