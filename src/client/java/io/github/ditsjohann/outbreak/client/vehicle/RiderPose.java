package io.github.ditsjohann.outbreak.client.vehicle;

import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * How someone sits on or in a vehicle, seen from outside: hands on the steering wheel, pedalling a
 * bicycle with hands on the bars, feet on a moped's floorboard, the moped's passenger holding on.
 * Kept on the render state (see {@link Holder}) and applied after vanilla's sitting pose.
 */
public enum RiderPose {
	NONE,
	CAR_DRIVER,
	CYCLIST,
	MOPED_RIDER,
	MOPED_PASSENGER;

	/** Mixed into {@link HumanoidRenderState}. */
	public interface Holder {
		RiderPose outbreak$riderPose();

		void outbreak$setRiderPose(RiderPose pose, float pedal);

		float outbreak$pedal();
	}

	public static void extract(LivingEntity entity, HumanoidRenderState state, float partialTicks) {
		Holder holder = (Holder) state;
		if (!(entity.getVehicle() instanceof Vehicle vehicle)) {
			holder.outbreak$setRiderPose(NONE, 0.0F);
			return;
		}

		int seat = vehicle.getPassengers().indexOf(entity);
		VehicleType type = vehicle.vehicleType();
		RiderPose pose = switch (type) {
			case BICYCLE -> CYCLIST;
			case SCOOTER -> seat == 0 ? MOPED_RIDER : MOPED_PASSENGER;
			default -> seat == 0 ? CAR_DRIVER : NONE;
		};
		holder.outbreak$setRiderPose(pose, Mth.lerp(partialTicks, vehicle.wheelRotationO, vehicle.wheelRotation) * 0.6F);
	}

	public static void apply(HumanoidModel<?> model, HumanoidRenderState state) {
		Holder holder = (Holder) state;
		switch (holder.outbreak$riderPose()) {
			case CAR_DRIVER -> arms(model, -1.25F, 0.28F);
			case CYCLIST -> {
				arms(model, -0.8F, 0.0F);
				// The pedals go round with the wheels; the legs follow them.
				float pedal = holder.outbreak$pedal();
				model.rightLeg.xRot = -0.55F - 0.4F * Mth.cos(pedal);
				model.leftLeg.xRot = -0.55F + 0.4F * Mth.cos(pedal);
				model.rightLeg.yRot = 0.05F;
				model.leftLeg.yRot = -0.05F;
				model.rightLeg.zRot = 0.0F;
				model.leftLeg.zRot = 0.0F;
			}
			case MOPED_RIDER -> {
				arms(model, -1.15F, 0.0F);
				model.rightLeg.xRot = -1.15F;
				model.leftLeg.xRot = -1.15F;
				model.rightLeg.yRot = 0.12F;
				model.leftLeg.yRot = -0.12F;
			}
			case MOPED_PASSENGER -> arms(model, -1.1F, 0.4F);
			case NONE -> {
			}
		}
	}

	/** Both arms forward, turned in by inward radians (when not busy swinging something). */
	private static void arms(HumanoidModel<?> model, float forward, float inward) {
		model.rightArm.xRot = forward;
		model.leftArm.xRot = forward;
		model.rightArm.yRot = -inward;
		model.leftArm.yRot = inward;
		model.rightArm.zRot = 0.0F;
		model.leftArm.zRot = 0.0F;
	}
}
