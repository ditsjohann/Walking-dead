package io.github.ditsjohann.outbreak.client.walker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.ditsjohann.outbreak.client.model.BoxModels;
import io.github.ditsjohann.outbreak.gore.Limb;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** One severed piece of a walker: its cut end showing, no stumps (those belong to the body it came off). */
public class BodyPartModel extends EntityModel<BodyPartRenderState> {
	private final List<ModelPart> cuts = new ArrayList<>();
	private final List<ModelPart> stumps = new ArrayList<>();

	public BodyPartModel(ModelPart root, Limb limb) {
		super(root);
		Map<String, ModelPart> parts = BoxModels.parts(root, pieceJson(limb));
		parts.forEach((name, part) -> {
			if (name.endsWith("_cut")) {
				this.cuts.add(part);
			} else if (name.endsWith("_stump")) {
				this.stumps.add(part);
			}
		});
	}

	private static JsonObject pieceJson(Limb limb) {
		JsonObject json = new JsonObject();
		JsonArray parts = new JsonArray();
		parts.add(WalkerModel.branch(limb));
		json.add("parts", parts);
		return json;
	}

	@Override
	public void setupAnim(BodyPartRenderState state) {
		super.setupAnim(state);
		for (ModelPart cut : this.cuts) {
			cut.visible = true;
		}

		for (ModelPart stump : this.stumps) {
			stump.visible = false;
		}
	}
}
