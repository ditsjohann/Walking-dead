package io.github.ditsjohann.outbreak.client.mixin;

import io.github.ditsjohann.outbreak.client.vehicle.RiderPose;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Where a humanoid sits on a vehicle, for {@link RiderPose}. */
@Mixin(HumanoidRenderState.class)
abstract class HumanoidRenderStateMixin implements RiderPose.Holder {
	@Unique
	private RiderPose outbreak$riderPose = RiderPose.NONE;
	@Unique
	private float outbreak$pedal;

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
}
