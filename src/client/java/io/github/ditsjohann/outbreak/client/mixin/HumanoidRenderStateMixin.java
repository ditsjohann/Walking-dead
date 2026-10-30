package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.vehicle.RiderPose;
import io.github.ditsjohann.outbreak.client.world.GutsLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Where a humanoid sits on a vehicle (for {@link RiderPose}), and whether it's covered in guts (for {@link GutsLayer}). */
@Mixin(HumanoidRenderState.class)
abstract class HumanoidRenderStateMixin implements RiderPose.Holder, GutsLayer.Holder {
	@Unique
	private RiderPose outbreak$riderPose = RiderPose.NONE;
	@Unique
	private float outbreak$pedal;
	@Unique
	private boolean outbreak$guts;

	@Override
	public RiderPose outbreak$riderPose() {
		return this.outbreak$riderPose;
	}

	@Override
	public void outbreak$setRiderPose(RiderPose pose, float pedal) {
		this.outbreak$riderPose = pose;
		this.outbreak$pedal = pedal;
	}

	@Override
	public float outbreak$pedal() {
		return this.outbreak$pedal;
	}

	@Override
	public boolean outbreak$guts() {
		return this.outbreak$guts;
	}

	@Override
	public void outbreak$setGuts(boolean guts) {
		this.outbreak$guts = guts;
	}
}
