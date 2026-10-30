package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ditsjohann.outbreak.client.model.BoxModels;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * A vehicle model, built from the box tree that tools/vehicles/make_vehicles.py writes
 * (assets/outbreak/vehicles/&lt;type&gt;.json, see {@link BoxModels}): wheels spin and steer, the
 * steering wheel turns, the needles and the pedals move, the key shows when it's in. Made twice per
 * type: once for the body (glass left out) and once for only the glass, drawn see-through.
 */
public class VehicleModel extends EntityModel<VehicleRenderState> {
	/** How far the steering wheel turns for the front wheels' angle. */
	private static final float STEERING_RATIO = 2.0F;
	/** The needles sweep from -SWEEP (empty) to +SWEEP (full). */
	private static final float SWEEP = 2.3F;

	private final boolean glassOnly;
	private final List<ModelPart> wheels = new ArrayList<>();
	private final List<ModelPart> glass = new ArrayList<>();
	private final List<ModelPart> others = new ArrayList<>();
	private final List<ModelPart> steering = new ArrayList<>();
	private final @Nullable ModelPart steeringWheel;
	private final @Nullable ModelPart speedNeedle;
	private final @Nullable ModelPart fuelNeedle;
	private final @Nullable ModelPart key;
	private final @Nullable ModelPart ignition;
	private final @Nullable ModelPart crank;
	/** From the root down to the part, for working out where it is. */
	private final Map<String, List<ModelPart>> chains;
	private final int[] tints;

	public VehicleModel(ModelPart root, VehicleType type, boolean glassOnly) {
		super(root);
		this.glassOnly = glassOnly;
		JsonObject json = read(type);
		Map<String, ModelPart> parts = BoxModels.parts(root, json);
		this.chains = BoxModels.chains(root, json);
		for (int wheel = 0; wheel < type.wheels; wheel++) {
			this.wheels.add(parts.get("wheel_" + wheel));
		}

		parts.forEach((name, part) -> (name.startsWith("glass") ? this.glass : this.others).add(part));
		if (parts.containsKey("steer")) {
			this.steering.add(parts.get("steer"));
		} else if (type.isCar()) {
			this.steering.add(parts.get("wheel_0"));
			this.steering.add(parts.get("wheel_1"));
		}

		this.steeringWheel = parts.get("steering_wheel");
		this.speedNeedle = parts.get("needle_speed");
		this.fuelNeedle = parts.get("needle_fuel");
		this.key = parts.get("key");
		this.ignition = parts.get("ignition");
		this.crank = parts.get("crank");
		JsonArray tints = json.getAsJsonArray("tints");
		this.tints = new int[tints.size()];
		for (int i = 0; i < this.tints.length; i++) {
			this.tints[i] = tints.get(i).getAsInt();
		}
	}

	/** The colour the paint layer is tinted with (ARGB). */
	public int tint(int color) {
		return this.tints.length == 0 ? -1 : this.tints[Math.floorMod(color, this.tints.length)];
	}

	@Override
	public void setupAnim(VehicleRenderState state) {
		super.setupAnim(state);
		for (int i = 0; i < this.wheels.size(); i++) {
			ModelPart wheel = this.wheels.get(i);
			Vehicle.TireState tire = state.tires[i];
			wheel.visible = tire != Vehicle.TireState.MISSING;
			wheel.xRot = state.wheelRotation;
			if (tire == Vehicle.TireState.FLAT) {
				// Squashed: the wheel sits a pixel and a half lower.
				wheel.y += 1.5F;
				wheel.yScale = 0.8F;
			}
		}

		// A positive yaw in the model turns the front to the vehicle's right: steering left is negative.
		for (ModelPart part : this.steering) {
			part.yRot = -state.steer;
		}

		if (this.steeringWheel != null) {
			this.steeringWheel.zRot = -state.steer * STEERING_RATIO;
		}

		if (this.crank != null) {
			this.crank.xRot = state.wheelRotation * 0.6F;
		}

		if (this.speedNeedle != null) {
			this.speedNeedle.zRot = needle(state.speedFraction);
		}

		if (this.fuelNeedle != null) {
			this.fuelNeedle.zRot = needle(state.fuelFraction);
		}

		if (this.key != null) {
			this.key.visible = state.keyInside;
			this.key.zRot = state.running ? -1.1F : 0.0F;
		}

		if (this.ignition != null && state.ignitionFocus > 0.0F) {
			// Looked at: the ignition (and the key in it) swells a little, pulsing, so you see what you'll click.
			float scale = 1.3F * state.ignitionFocus;
			this.ignition.xScale = scale;
			this.ignition.yScale = scale;
			this.ignition.zScale = scale;
		}

		if (this.glassOnly) {
			// Only the glass: everything else is still walked (the glass hangs off it) but not drawn.
			for (ModelPart part : this.others) {
				part.skipDraw = true;
			}

			for (ModelPart part : this.glass) {
				part.visible = state.showGlass;
				part.skipDraw = false;
			}
		} else {
			for (ModelPart part : this.glass) {
				part.visible = false;
			}
		}
	}

	private static float needle(float fraction) {
		return -Mth.lerp(Mth.clamp(fraction, 0.0F, 1.0F), -SWEEP, SWEEP);
	}

	public boolean has(String part) {
		return this.chains.containsKey(part);
	}

	/**
	 * Where a part's pivot is for this state, in the model's own space (blocks; x right, y down, z
	 * forward), or null if the model has no such part.
	 */
	public @Nullable Vector3f locate(VehicleRenderState state, String part) {
		List<ModelPart> chain = this.chains.get(part);
		if (chain == null) {
			return null;
		}

		this.setupAnim(state);
		PoseStack poseStack = new PoseStack();
		for (ModelPart link : chain) {
			link.translateAndRotate(poseStack);
		}

		return poseStack.last().pose().transformPosition(new Vector3f());
	}

	public static LayerDefinition load(VehicleType type) {
		return BoxModels.layer(read(type), 0.0F);
	}

	private static JsonObject read(VehicleType type) {
		return BoxModels.read("/assets/outbreak/vehicles/" + type.id() + ".json");
	}
}
