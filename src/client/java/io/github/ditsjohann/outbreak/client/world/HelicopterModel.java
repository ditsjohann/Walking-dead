package io.github.ditsjohann.outbreak.client.world;

import java.util.Map;

import com.google.gson.JsonObject;

import io.github.ditsjohann.outbreak.client.model.BoxModels;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/** The helicopter (made by tools/world/make_world.py): the main rotor and the tail rotor spin. */
public class HelicopterModel extends EntityModel<HelicopterRenderState> {
	private final ModelPart rotor;
	private final ModelPart tailRotor;

	public HelicopterModel(ModelPart root) {
		super(root);
		Map<String, ModelPart> parts = BoxModels.parts(root, read());
		this.rotor = parts.get("rotor");
		this.tailRotor = parts.get("tail_rotor");
	}

	@Override
	public void setupAnim(HelicopterRenderState state) {
		super.setupAnim(state);
		this.rotor.yRot = state.rotor;
		this.tailRotor.xRot = state.rotor * 2.5F;
	}

	public static LayerDefinition load() {
		return BoxModels.layer(read(), 0.0F);
	}

	private static JsonObject read() {
		return BoxModels.read("/assets/outbreak/vehicles/helicopter.json");
	}
}
