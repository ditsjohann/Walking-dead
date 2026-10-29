package io.github.ditsjohann.outbreak.client.vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import io.github.ditsjohann.outbreak.client.model.BoxModels;
import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * A vehicle model, built from the box tree that tools/vehicles/make_vehicles.py writes
 * (assets/outbreak/vehicles/&lt;type&gt;.json, see {@link BoxModels}): wheels spin and steer, glass can be hidden.
 */
public class VehicleModel extends EntityModel<VehicleRenderState> {
	private final List<ModelPart> wheels = new ArrayList<>();
	private final List<ModelPart> glass = new ArrayList<>();
	private final List<ModelPart> steering = new ArrayList<>();

	public VehicleModel(ModelPart root, VehicleType type) {
		super(root);
		Map<String, ModelPart> parts = new HashMap<>();
		parts.putAll(BoxModels.parts(root, read(type)));
		for (int wheel = 0; wheel < type.wheels; wheel++) {
			this.wheels.add(parts.get("wheel_" + wheel));
		}

		parts.forEach((name, part) -> {
			if (name.startsWith("glass")) {
				this.glass.add(part);
			}
		});
		if (parts.containsKey("steer")) {
			this.steering.add(parts.get("steer"));
		} else if (type.isCar()) {
			this.steering.add(parts.get("wheel_0"));
			this.steering.add(parts.get("wheel_1"));
		}
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

		for (ModelPart part : this.steering) {
			part.yRot = state.steer;
		}

		for (ModelPart part : this.glass) {
			part.visible = state.showGlass;
		}
	}

	public static LayerDefinition load(VehicleType type) {
		return BoxModels.layer(read(type), 0.0F);
	}

	private static JsonObject read(VehicleType type) {
		return BoxModels.read("/assets/outbreak/vehicles/" + type.id() + ".json");
	}
}
