package io.github.ditsjohann.outbreak.client.walker;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.github.ditsjohann.outbreak.Outbreak;
import io.github.ditsjohann.outbreak.entity.Walker;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws walkers: one of the skins, the jointed model, and the whole-body motion the model can't do
 * by itself: falling over the way a hit sent it, lying there, getting up, crawling on its belly,
 * and corpses that stay a while and then sink away.
 */
public class WalkerRenderer extends MobRenderer<Walker, WalkerRenderState, WalkerModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Outbreak.id("walker"), "main");
	private static final Identifier[] SKINS = new Identifier[Walker.SKINS];

	static {
		for (int i = 0; i < Walker.SKINS; i++) {
			SKINS[i] = Outbreak.id("textures/entity/walker/walker_" + i + ".png");
		}
	}

	public WalkerRenderer(EntityRendererProvider.Context context) {
		super(context, new WalkerModel(context.bakeLayer(LAYER)), 0.5F);
	}

	@Override
	public WalkerRenderState createRenderState() {
		return new WalkerRenderState();
	}

	public static Identifier skinTexture(int skin) {
		return SKINS[Math.floorMod(skin, SKINS.length)];
	}

	@Override
	public Identifier getTextureLocation(WalkerRenderState state) {
		return skinTexture(state.skin);
	}

	@Override
	public void extractRenderState(Walker walker, WalkerRenderState state, float partialTicks) {
		super.extractRenderState(walker, state, partialTicks);
		long seed = walker.getUUID().getLeastSignificantBits();
		state.skin = walker.skin();
		state.gait = (int) Math.floorMod(seed, 5L);
		state.headTilt = ((seed >>> 8) & 0xFF) / 255.0F * 0.5F - 0.25F;
		state.crawler = walker.isCrawler();
		state.lostLimbs = walker.lostLimbs();
		state.aggressive = walker.isAggressive();
		state.posture = walker.posture();
		float now = walker.level().getGameTime() + partialTicks;
		state.postureTime = now - walker.postureSince();
		state.deadTime = walker.deathTime > 0 ? walker.deathTime + partialTicks : 0.0F;
		state.corpseTicks = walker.corpseTicks();
		state.fallAngle = Mth.wrapDegrees(walker.fallYaw() - state.bodyRot);
		state.attack = walker.getSwingAnimation(partialTicks);
		// No red tint on corpses: only while being hurt.
		state.hasRedOverlay = walker.hurtTime > 0;
		WalkerAnimator.of(walker).update(walker, walker.tickCount + partialTicks, state.bodyRot, state.springs);
	}

	@Override
	protected void setupRotations(WalkerRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
		// From here on: forward is -z, right is +x.
		Quaternionf prone = lying(0.0F, 1.0F);
		Quaternionf fallen = lying(state.fallAngle, 1.0F);
		Quaternionf pose;
		if (state.deadTime > 0.0F) {
			pose = lying(state.fallAngle, fallCurve(state.deadTime, 8.0F));
			if (state.crawler) {
				pose = new Quaternionf(prone).slerp(fallen, Math.min(1.0F, state.deadTime / 8.0F));
			}
		} else if (state.posture == Walker.Posture.DOWN) {
			pose = lying(state.fallAngle, fallCurve(state.postureTime, Walker.FALL_TICKS));
		} else if (state.posture == Walker.Posture.GETTING_UP) {
			float up = ease(Mth.clamp(state.postureTime / Walker.GET_UP_TICKS, 0.0F, 1.0F));
			pose = new Quaternionf(fallen).slerp(state.crawler ? prone : new Quaternionf(), up);
		} else if (state.crawler) {
			pose = prone;
		} else {
			return;
		}

		// How far over it is (0 upright, 1 flat), from the quaternion itself.
		float over = Mth.clamp(1.0F - pose.transform(new Vector3f(0.0F, 1.0F, 0.0F)).y, 0.0F, 1.0F);
		org.joml.Vector3f along = pose.transform(new Vector3f(0.0F, 1.0F, 0.0F));
		// Lying, the hips are where the walker is (not the feet), and the body rests on the ground, not in it.
		poseStack.translate(-along.x * 0.85F, 0.14F * over, -along.z * 0.85F);
		if (state.deadTime > state.corpseTicks - 20) {
			poseStack.translate(0.0F, -(state.deadTime - (state.corpseTicks - 20)) / 20.0F * 0.35F, 0.0F);
		}

		poseStack.rotate(pose);
	}

	/** The rotation that tips the body over towards angle (relative to its facing), amount 0-1 of the way down. */
	private static Quaternionf lying(float angle, float amount) {
		float rad = angle * Mth.DEG_TO_RAD;
		float dx = Mth.sin(rad);
		float dz = -Mth.cos(rad);
		return new Quaternionf().rotationAxis(amount * Mth.HALF_PI, dz, 0.0F, -dx);
	}

	/** Falling: speeding up like a dropped plank, then a little bounce on the ground. */
	private static float fallCurve(float time, float duration) {
		if (time < duration) {
			float t = time / duration;
			return t * t;
		}

		float after = time - duration;
		return 1.0F - 0.07F * (float) Math.exp(-after / 2.0F) * Math.abs(Mth.sin(after * 1.6F));
	}

	private static float ease(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
