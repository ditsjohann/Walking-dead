package io.github.ditsjohann.outbreak.client.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.world.Helicopter;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws the helicopter, rotors spinning, from as far off as it can be seen. */
public class HelicopterRenderer extends EntityRenderer<Helicopter, HelicopterRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Outbreak.id("helicopter"), "main");
	private static final Identifier TEXTURE = Outbreak.id("textures/entity/helicopter.png");
	/** Radians a tick. */
	private static final float ROTOR_SPEED = 1.3F;

	private final HelicopterModel model;

	public HelicopterRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new HelicopterModel(context.bakeLayer(LAYER));
	}

	@Override
	public HelicopterRenderState createRenderState() {
		return new HelicopterRenderState();
	}

	@Override
	public void extractRenderState(Helicopter helicopter, HelicopterRenderState state, float partialTicks) {
		super.extractRenderState(helicopter, state, partialTicks);
		state.yRot = helicopter.getYRot(partialTicks);
		state.rotor = (helicopter.tickCount + partialTicks) * ROTOR_SPEED;
	}

	@Override
	public void submit(HelicopterRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, -state.yRot);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		collector.submitModel(this.model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
