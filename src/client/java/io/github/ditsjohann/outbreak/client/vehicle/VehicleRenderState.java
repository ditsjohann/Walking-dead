package io.github.ditsjohann.outbreak.client.vehicle;

import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class VehicleRenderState extends EntityRenderState {
	public VehicleType type = VehicleType.HATCHBACK;
	public float yRot;
	public float hurtTime;
	public int hurtDir;
	public float wheelRotation;
	/** Radians: positive turns the front wheels left. */
	public float steer;
	public Vehicle.TireState[] tires = new Vehicle.TireState[4];
	/** Hidden once the windows are broken, and for whoever sits inside in first person (so they can see out). */
	public boolean showGlass = true;
	public int color;
	public boolean wreck;
}
