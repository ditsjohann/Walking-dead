package io.github.ditsjohann.outbreak.client.vehicle;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.ditsjohann.outbreak.vehicle.Vehicle;
import io.github.ditsjohann.outbreak.vehicle.VehicleType;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A vehicle model, built from the box tree that tools/vehicles/make_vehicles.py writes
 * (assets/outbreak/vehicles/&lt;type&gt;.json): wheels spin and steer, glass can be hidden.
 * The JSON is in pixels with y up; Minecraft models have y down.
 */
public class VehicleModel extends EntityModel<VehicleRenderState> {
	private final List<ModelPart> wheels = new ArrayList<>();
	private final List<ModelPart> glass = new ArrayList<>();
	private final List<ModelPart> steering = new ArrayList<>();

	public VehicleModel(ModelPart root, VehicleType type) {
		super(root);
		Map<String, ModelPart> parts = new HashMap<>();
		collect(root, read(type).getAsJsonArray("parts"), parts);
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

	/** Every part by name (ModelPart doesn't list its children, so walk the JSON alongside). */
	private static void collect(ModelPart part, JsonArray children, Map<String, ModelPart> out) {
		for (JsonElement element : children) {
			JsonObject json = element.getAsJsonObject();
			String name = json.get("name").getAsString();
			ModelPart child = part.getChild(name);
			out.put(name, child);
			collect(child, json.getAsJsonArray("children"), out);
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
		JsonObject json = read(type);
		MeshDefinition mesh = new MeshDefinition();
		for (JsonElement part : json.getAsJsonArray("parts")) {
			addPart(mesh.getRoot(), part.getAsJsonObject());
		}

		JsonArray texture = json.getAsJsonArray("texture");
		return LayerDefinition.create(mesh, texture.get(0).getAsInt(), texture.get(1).getAsInt());
	}

	private static JsonObject read(VehicleType type) {
		String path = "/assets/outbreak/vehicles/" + type.id() + ".json";
		try (InputStream in = VehicleModel.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing vehicle model " + path);
			}

			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static void addPart(PartDefinition parent, JsonObject json) {
		String name = json.get("name").getAsString();
		float[] pivot = floats(json.getAsJsonArray("pivot"));
		float[] rotation = floats(json.getAsJsonArray("rotation"));
		CubeListBuilder cubes = CubeListBuilder.create();
		for (JsonElement element : json.getAsJsonArray("boxes")) {
			JsonObject box = element.getAsJsonObject();
			float[] origin = floats(box.getAsJsonArray("origin"));
			float[] size = floats(box.getAsJsonArray("size"));
			float[] uv = floats(box.getAsJsonArray("uv"));
			float inflate = box.has("inflate") ? box.get("inflate").getAsFloat() : 0.0F;
			cubes.texOffs((int) uv[0], (int) uv[1])
				.addBox(origin[0], -(origin[1] + size[1]), origin[2], size[0], size[1], size[2], new CubeDeformation(inflate));
		}

		// y flips, and with it the sense of rotations about x and z.
		PartDefinition part = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
			pivot[0], -pivot[1], pivot[2],
			-rotation[0] * Mth.DEG_TO_RAD, rotation[1] * Mth.DEG_TO_RAD, -rotation[2] * Mth.DEG_TO_RAD));
		for (JsonElement child : json.getAsJsonArray("children")) {
			addPart(part, child.getAsJsonObject());
		}
	}

	private static float[] floats(JsonArray array) {
		float[] values = new float[array.size()];
		for (int i = 0; i < values.length; i++) {
			values[i] = array.get(i).getAsFloat();
		}

		return values;
	}
}
