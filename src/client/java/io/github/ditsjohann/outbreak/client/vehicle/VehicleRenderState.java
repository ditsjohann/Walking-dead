package io.github.ditsjohann.outbreak.client.vehicle;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public class VehicleRenderState extends EntityRenderState {
	public VehicleType type = VehicleType.HATCHBACK;
	public float yRot;
	public float hurtTime;
	public int hurtDir;
	public float wheelRotation;
	/** Radians: positive turns the front wheels left. */
	public float steer;
	public Vehicle.TireState[] tires = new Vehicle.TireState[4];
	/** Hidden once the windows are broken. */
	public boolean showGlass = true;
	public int color;
	public boolean wreck;
	public boolean keyInside;
	/** You're looking at the ignition: it and the key stand out. */
	public float ignitionFocus;
	public boolean running;
	public float speedFraction;
	public float fuelFraction;
	/** Set when you're driving it in first person: your eye in the model's space, for your arms. */
	public @Nullable Vector3f driverEye;
	public @Nullable Identifier driverSkin;
	public boolean driverSlim;
	public boolean driverSleeves;
}
