package io.github.ditsjohann.outbreak.client.walker;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.gore.BodyPart;
import io.github.ditsjohann.outbreak.gore.Limb;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Severed arms, legs and heads, in the skin of the walker they came off, tumbling and then lying where they fell. */
public class BodyPartRenderer extends EntityRenderer<BodyPart, BodyPartRenderState> {
	public static final Map<Limb, ModelLayerLocation> LAYERS = new EnumMap<>(Limb.class);

	static {
		for (Limb limb : Limb.values()) {
			LAYERS.put(limb, new ModelLayerLocation(Outbreak.id("body_part"), limb.name().toLowerCase(java.util.Locale.ROOT)));
		}
	}

	private final Map<Limb, BodyPartModel> models = new EnumMap<>(Limb.class);

	public BodyPartRenderer(EntityRendererProvider.Context context) {
		super(context);
		for (Limb limb : Limb.values()) {
			this.models.put(limb, new BodyPartModel(context.bakeLayer(LAYERS.get(limb)), limb));
		}

		this.shadowRadius = 0.2F;
	}

	@Override
	public BodyPartRenderState createRenderState() {
		return new BodyPartRenderState();
	}

	@Override
	public void extractRenderState(BodyPart part, BodyPartRenderState state, float partialTicks) {
		super.extractRenderState(part, state, partialTicks);
		state.limb = part.limb();
		state.skin = part.skin();
		state.yRot = part.getYRot();
		state.tumble = Mth.lerp(partialTicks, part.tumbleO, part.tumble);
	}

	@Override
	public void submit(BodyPartRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		if (state.limb == Limb.HEAD) {
			// A head rolls: turn it over about its middle.
			poseStack.translate(0.0F, 0.25F, 0.0F);
			poseStack.rotateDegrees(Axis.XP, state.tumble);
			poseStack.translate(0.0F, 0.25F, 0.0F);
		} else {
			// A limb lies flat along the ground, cut end first, rolled over by the tumble.
			float length = state.limb.lower != null ? 0.75F : 0.375F;
			poseStack.translate(0.0F, 0.13F, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, state.tumble);
			poseStack.rotateDegrees(Axis.XP, -90.0F);
			poseStack.translate(0.0F, length / 2.0F, 0.0F);
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(this.models.get(state.limb), state, poseStack, WalkerRenderer.skinTexture(state.skin), state.lightCoords,
			OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
