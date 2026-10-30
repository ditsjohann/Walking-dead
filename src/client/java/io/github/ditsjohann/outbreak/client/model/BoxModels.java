package io.github.ditsjohann.outbreak.client.model;

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

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Models made of boxes, written as JSON by our generator scripts (tools/vehicles, tools/walkers): a
 * tree of named parts, each with a pivot, a rotation and boxes, in pixels with y up. Minecraft
 * models have y down, so y (and the sense of rotations about x and z) flips; {@code groundY} is
 * where y = 0 ends up (0 for vehicles, 24 for anything drawn like a vanilla mob).
 */
public final class BoxModels {
	private BoxModels() {
	}

	public static JsonObject read(String path) {
		try (InputStream in = BoxModels.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("Missing model " + path);
			}

			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	public static LayerDefinition layer(JsonObject json, float groundY) {
		MeshDefinition mesh = new MeshDefinition();
		for (JsonElement part : json.getAsJsonArray("parts")) {
			addPart(mesh.getRoot(), part.getAsJsonObject(), groundY);
		}

		JsonArray texture = json.getAsJsonArray("texture");
		return LayerDefinition.create(mesh, texture.get(0).getAsInt(), texture.get(1).getAsInt());
	}

	/** Every part by name (ModelPart doesn't list its children, so walk the JSON alongside). */
	public static Map<String, ModelPart> parts(ModelPart root, JsonObject json) {
		Map<String, ModelPart> out = new HashMap<>();
		collect(root, json.getAsJsonArray("parts"), out);
		return out;
	}

	/** Every part by name, with the parts leading down to it from the root (itself last). */
	public static Map<String, List<ModelPart>> chains(ModelPart root, JsonObject json) {
		Map<String, List<ModelPart>> out = new HashMap<>();
		chain(root, json.getAsJsonArray("parts"), new ArrayList<>(), out);
		return out;
	}

	private static void chain(ModelPart part, JsonArray children, List<ModelPart> above, Map<String, List<ModelPart>> out) {
		for (JsonElement element : children) {
			JsonObject json = element.getAsJsonObject();
			ModelPart child = part.getChild(json.get("name").getAsString());
			List<ModelPart> path = new ArrayList<>(above);
			path.add(child);
			out.put(json.get("name").getAsString(), List.copyOf(path));
			chain(child, json.getAsJsonArray("children"), path, out);
		}
	}

	private static void collect(ModelPart part, JsonArray children, Map<String, ModelPart> out) {
		for (JsonElement element : children) {
			JsonObject json = element.getAsJsonObject();
			String name = json.get("name").getAsString();
			ModelPart child = part.getChild(name);
			out.put(name, child);
			collect(child, json.getAsJsonArray("children"), out);
		}
	}

	private static void addPart(PartDefinition parent, JsonObject json, float yOffset) {
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

		PartDefinition part = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(
			pivot[0], yOffset - pivot[1], pivot[2],
			-rotation[0] * Mth.DEG_TO_RAD, rotation[1] * Mth.DEG_TO_RAD, -rotation[2] * Mth.DEG_TO_RAD));
		for (JsonElement child : json.getAsJsonArray("children")) {
			addPart(part, child.getAsJsonObject(), 0.0F);
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
